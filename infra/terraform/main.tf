# Events and storage exist in every environment: Floci for local development (SNS, SQS and the
# optical files bucket only), AWS in prod. The rest is the AWS platform for the kubeadm cluster and is
# created only with platform_enabled = true, which only prod sets.

locals {
  name          = "dershane-${var.environment}"
  secret_prefix = "dershane"

  platform = var.platform_enabled ? 1 : 0

  # Which AWS calls each service makes. Topic keys are producer names (topics), queue keys consumer
  # queues (subscriptions), bucket keys entries of local.buckets. api-gateway and keycloak call no
  # AWS API. A new consumer or producer adds itself here as well as in events.auto.tfvars.
  service_access = {
    user          = { publish = ["user"] }
    attendance    = { publish = ["attendance"], consume = ["attendance-user-events"] }
    task          = { publish = ["task"], consume = ["task-user-events"] }
    messaging     = { publish = ["messaging"], consume = ["messaging-user-events"] }
    exam-analysis = { publish = ["exam-analysis"], consume = ["exam-analysis-user-events"], buckets = ["exam-analysis-optical-files"] }
    notification  = { consume = ["notification-queue"] }
  }

  # One database and role per service, as in local/postgres/init.sql.
  databases = ["user_service", "attendance_service", "task_service", "messaging_service", "exam_analysis", "notification", "keycloak"]

  # cp = 10.0.1.10, w1 = 10.0.1.11, ... Fixed so a recreated node keeps the address kubeadm knows.
  nodes = var.platform_enabled ? merge(
    { cp = { role = "control-plane", instance_type = var.cp_instance_type, private_ip = cidrhost(module.network[0].public_subnet_cidr, 10) } },
    { for i in range(var.worker_count) : "w${i + 1}" => {
      role          = "worker"
      instance_type = var.worker_instance_type
      private_ip    = cidrhost(module.network[0].public_subnet_cidr, 11 + i)
    } },
  ) : {}

  buckets = merge(
    var.buckets,
    var.platform_enabled ? { "etcd-backups" = { versioning = true, expiration_days = 14 } } : {},
  )

  dlq_names = [for queue in keys(var.subscriptions) : "${queue}-dlq"]
}

module "events" {
  source = "./modules/events"

  topics                   = var.topics
  subscriptions            = var.subscriptions
  create_inspection_queues = var.create_inspection_queues
}

module "storage" {
  source = "./modules/storage"

  buckets       = local.buckets
  bucket_prefix = var.bucket_prefix
  force_destroy = var.force_destroy_buckets
}

# The optical files bucket used to be declared here; keep its state when it moves into the module.
moved {
  from = aws_s3_bucket.this
  to   = module.storage.aws_s3_bucket.this
}

# --- AWS platform -------------------------------------------------------------------------------

module "network" {
  source = "./modules/network"
  count  = local.platform

  name                    = local.name
  vpc_cidr                = var.vpc_cidr
  admin_cidrs             = var.admin_cidrs
  ingress_http_node_port  = var.ingress_http_node_port
  ingress_https_node_port = var.ingress_https_node_port
}

module "iam" {
  source = "./modules/iam"
  count  = local.platform

  name           = local.name
  pod_identity   = var.pod_identity
  k8s_namespace  = var.k8s_namespace
  secret_prefix  = local.secret_prefix
  service_access = local.service_access

  topic_arns        = module.events.topic_arns
  queue_arns        = module.events.queue_arns
  bucket_arns       = module.storage.bucket_arns
  etcd_bucket_arn   = module.storage.bucket_arns["etcd-backups"]
  extra_secret_arns = [module.database[0].master_secret_arn]
}

module "nodes" {
  source = "./modules/nodes"
  count  = local.platform

  name              = local.name
  nodes             = local.nodes
  subnet_id         = module.network[0].public_subnet_id
  security_group_id = module.network[0].nodes_security_group_id

  control_plane_instance_profile = module.iam[0].control_plane_instance_profile
  worker_instance_profile        = module.iam[0].worker_instance_profile
  imds_hop_limit                 = module.iam[0].imds_hop_limit

  ssh_public_key = var.ssh_public_key
  volume_size    = var.node_volume_size
  secret_prefix  = local.secret_prefix
}

module "registry" {
  source = "./modules/registry"
  count  = local.platform

  name         = local.name
  repositories = var.ecr_repositories
  force_delete = var.force_destroy_buckets
  github_repo  = var.github_repo

  github_oidc_subject_prefix = var.github_oidc_subject_prefix
}

module "database" {
  source = "./modules/database"
  count  = local.platform

  name              = local.name
  subnet_ids        = module.network[0].private_subnet_ids
  security_group_id = module.network[0].rds_security_group_id
  instance_class    = var.db_instance_class
  allocated_storage = var.db_allocated_storage
  databases         = toset(local.databases)
  secret_prefix     = local.secret_prefix
  password_version  = var.db_password_version

  skip_final_snapshot = var.db_skip_final_snapshot
}

module "cache" {
  source = "./modules/cache"
  count  = var.platform_enabled && !var.standby ? 1 : 0

  name               = local.name
  subnet_ids         = module.network[0].private_subnet_ids
  security_group_id  = module.network[0].redis_security_group_id
  node_type          = var.cache_node_type
  secret_prefix      = local.secret_prefix
  auth_token_version = var.cache_auth_token_version
}

module "edge" {
  source = "./modules/edge"
  count  = local.platform

  name                = local.name
  enabled             = !var.standby
  vpc_id              = module.network[0].vpc_id
  subnet_id           = module.network[0].public_subnet_id
  security_group_id   = module.network[0].nlb_security_group_id
  target_instance_ids = module.nodes[0].worker_instance_ids
  http_node_port      = var.ingress_http_node_port
  https_node_port     = var.ingress_https_node_port
}

module "email" {
  source = "./modules/email"
  count  = var.platform_enabled && var.domain != null ? 1 : 0

  name            = local.name
  domain          = var.domain
  test_recipients = var.ses_test_recipients
  secret_prefix   = local.secret_prefix
}

module "monitoring" {
  source = "./modules/monitoring"
  count  = local.platform

  name        = local.name
  alert_email = var.alert_email
  dlq_names   = local.dlq_names
}
