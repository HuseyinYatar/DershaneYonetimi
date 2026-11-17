# Who may call which AWS API.
#   Nodes: an instance profile per role (worker, control-plane); ECR pulls and SSM Session Manager.
#   Pods:  one policy per service, built from service_access. pod_identity = "irsa" attaches each
#          policy to that service's own role (IRSA, see irsa.tf); "node" attaches all of them to the
#          worker role, so every pod gets every permission.
# No access keys are created here: nodes use their instance profile, pods use temporary STS keys.

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  region     = data.aws_region.current.region

  secret_arn_prefix = "arn:aws:secretsmanager:${local.region}:${local.account_id}:secret:${var.secret_prefix}"

  # Statements are added only when the list has entries; IAM rejects an empty Resource.
  service_statements = {
    for service, access in var.service_access : service => concat(
      length(access.publish) == 0 ? [] : [{
        Sid      = "PublishEvents"
        Effect   = "Allow"
        Action   = ["sns:Publish"]
        Resource = [for topic in access.publish : var.topic_arns[topic]]
      }],
      length(access.consume) == 0 ? [] : [{
        Sid    = "ConsumeEvents"
        Effect = "Allow"
        Action = [
          "sqs:GetQueueUrl",
          "sqs:GetQueueAttributes",
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:ChangeMessageVisibility",
        ]
        Resource = [for queue in access.consume : var.queue_arns[queue]]
      }],
      length(access.buckets) == 0 ? [] : [
        {
          Sid      = "BucketObjects"
          Effect   = "Allow"
          Action   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
          Resource = [for bucket in access.buckets : "${var.bucket_arns[bucket]}/*"]
        },
        {
          Sid      = "BucketList"
          Effect   = "Allow"
          Action   = ["s3:ListBucket"]
          Resource = [for bucket in access.buckets : var.bucket_arns[bucket]]
        },
      ],
    )
  }

  # A service with no AWS calls (api-gateway, keycloak) gets no policy and no role.
  service_policies = { for service, statements in local.service_statements : service => statements if length(statements) > 0 }
}

# --- Nodes --------------------------------------------------------------------------------------

resource "aws_iam_role" "node" {
  for_each = toset(["worker", "control-plane"])
  name     = "${var.name}-${each.key}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "node_ssm" {
  for_each   = aws_iam_role.node
  role       = each.value.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "node_ecr" {
  for_each   = aws_iam_role.node
  role       = each.value.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_instance_profile" "node" {
  for_each = aws_iam_role.node
  name     = each.value.name
  role     = each.value.name
}

# etcd snapshots, the IRSA public keys and the kubeconfig secret are written from the control plane.
resource "aws_iam_role_policy" "control_plane" {
  name = "cluster-maintenance"
  role = aws_iam_role.node["control-plane"].name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      [
        {
          Sid      = "EtcdBackups"
          Effect   = "Allow"
          Action   = ["s3:PutObject", "s3:GetObject", "s3:ListBucket"]
          Resource = [var.etcd_bucket_arn, "${var.etcd_bucket_arn}/*"]
        },
        {
          Sid      = "ClusterSecrets"
          Effect   = "Allow"
          Action   = ["secretsmanager:PutSecretValue", "secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
          Resource = ["${local.secret_arn_prefix}/cluster/*"]
        },
      ],
      var.pod_identity == "irsa" ? [{
        Sid      = "OidcKeys"
        Effect   = "Allow"
        Action   = ["s3:PutObject"]
        Resource = ["${aws_s3_bucket.oidc[0].arn}/keys.json"]
      }] : [],
    )
  })
}

# --- Service permissions ------------------------------------------------------------------------

resource "aws_iam_policy" "service" {
  for_each = local.service_policies
  name     = "${var.name}-${each.key}"

  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = each.value
  })
}

# External Secrets Operator copies Secrets Manager entries into Kubernetes Secrets for the pods.
resource "aws_iam_policy" "external_secrets" {
  name = "${var.name}-external-secrets"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid      = "ReadSecrets"
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
      Resource = concat(["${local.secret_arn_prefix}/*"], var.extra_secret_arns)
    }]
  })
}

resource "aws_iam_role_policy_attachment" "worker_services" {
  for_each   = var.pod_identity == "node" ? aws_iam_policy.service : {}
  role       = aws_iam_role.node["worker"].name
  policy_arn = each.value.arn
}

resource "aws_iam_role_policy_attachment" "worker_external_secrets" {
  count      = var.pod_identity == "node" ? 1 : 0
  role       = aws_iam_role.node["worker"].name
  policy_arn = aws_iam_policy.external_secrets.arn
}
