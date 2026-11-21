# IRSA on a self-managed cluster. kubeadm signs service account tokens with /etc/kubernetes/pki/sa.key.
# AWS STS can verify them once it can read the issuer's discovery document and public keys, so both
# are served from this bucket:
#   .well-known/openid-configuration  written here
#   keys.json                         uploaded from the control plane after kubeadm init:
#                                     kubectl get --raw /openid/v1/jwks | aws s3 cp - s3://<bucket>/keys.json
# The API server must issue tokens with this issuer (kubeadm ClusterConfiguration, apiServer.extraArgs):
#   service-account-issuer   = <oidc_issuer output>
#   service-account-jwks-uri = <oidc_issuer output>/keys.json
# A pod then mounts a projected token (audience sts.amazonaws.com), gets AWS_ROLE_ARN and
# AWS_WEB_IDENTITY_TOKEN_FILE, and the SDK exchanges the token for temporary keys of its role.

locals {
  irsa = var.pod_identity == "irsa"

  oidc_bucket = "${var.name}-oidc-${local.account_id}"
  oidc_issuer = "https://${local.oidc_bucket}.s3.${local.region}.amazonaws.com"
  oidc_host   = trimprefix(local.oidc_issuer, "https://")

  # Service account per role: the application services in their namespace, plus External Secrets.
  irsa_roles = local.irsa ? merge(
    { for service, _ in local.service_policies : service => {
      namespace       = var.k8s_namespace
      service_account = service
      policy_arn      = aws_iam_policy.service[service].arn
    } },
    { external-secrets = {
      namespace       = "external-secrets"
      service_account = "external-secrets"
      policy_arn      = aws_iam_policy.external_secrets.arn
    } },
  ) : {}
}

resource "aws_s3_bucket" "oidc" {
  count         = local.irsa ? 1 : 0
  bucket        = local.oidc_bucket
  force_destroy = true
}

# STS fetches the two documents anonymously; everything else in the bucket stays private.
resource "aws_s3_bucket_public_access_block" "oidc" {
  count  = local.irsa ? 1 : 0
  bucket = aws_s3_bucket.oidc[0].id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = false
  restrict_public_buckets = false
}

resource "aws_s3_bucket_policy" "oidc" {
  count  = local.irsa ? 1 : 0
  bucket = aws_s3_bucket.oidc[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "PublicOidcDocuments"
      Effect    = "Allow"
      Principal = "*"
      Action    = "s3:GetObject"
      Resource = [
        "${aws_s3_bucket.oidc[0].arn}/.well-known/openid-configuration",
        "${aws_s3_bucket.oidc[0].arn}/keys.json",
      ]
    }]
  })

  depends_on = [aws_s3_bucket_public_access_block.oidc]
}

resource "aws_s3_object" "openid_configuration" {
  count        = local.irsa ? 1 : 0
  bucket       = aws_s3_bucket.oidc[0].id
  key          = ".well-known/openid-configuration"
  content_type = "application/json"

  content = jsonencode({
    issuer                                = local.oidc_issuer
    jwks_uri                              = "${local.oidc_issuer}/keys.json"
    authorization_endpoint                = "urn:kubernetes:programmatic_authorization"
    response_types_supported              = ["id_token"]
    subject_types_supported               = ["public"]
    id_token_signing_alg_values_supported = ["RS256"]
    claims_supported                      = ["sub", "iss"]
  })
}

resource "aws_iam_openid_connect_provider" "cluster" {
  count          = local.irsa ? 1 : 0
  url            = local.oidc_issuer
  client_id_list = ["sts.amazonaws.com"]
}

# Only system:serviceaccount:<namespace>:<name> with audience sts.amazonaws.com may take the role.
resource "aws_iam_role" "service" {
  for_each = local.irsa_roles
  name     = "${var.name}-${each.key}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.cluster[0].arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "${local.oidc_host}:sub" = "system:serviceaccount:${each.value.namespace}:${each.value.service_account}"
          "${local.oidc_host}:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy_attachment" "service" {
  for_each   = local.irsa_roles
  role       = aws_iam_role.service[each.key].name
  policy_arn = each.value.policy_arn
}
