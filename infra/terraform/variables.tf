# --- Environment --------------------------------------------------------------------------------

variable "aws_region" {
  type    = string
  default = "eu-central-1"
}

variable "environment" {
  description = "local (Floci) or prod (AWS). Part of resource names (dershane-<environment>-...) and tags."
  type        = string
  default     = "local"
}

variable "emulator" {
  description = "Floci (local only): skips the AWS account and credential checks an emulator cannot answer."
  type        = bool
  default     = false
}

variable "bucket_prefix" {
  description = "Prepended to bucket names, which are global on AWS. Empty locally so the services' defaults keep working."
  type        = string
  default     = ""
}

variable "force_destroy_buckets" {
  description = "Lets terraform destroy delete buckets that still hold objects."
  type        = bool
  default     = false
}

variable "platform_enabled" {
  description = "Creates everything beyond events and storage: network, nodes, IAM, ECR, RDS, ElastiCache, NLB, SES, alarms."
  type        = bool
  default     = false
}

variable "standby" {
  description = "Removes the NLB and ElastiCache while nobody works on the cluster. The Elastic IPs stay, so DNS keeps pointing at them."
  type        = bool
  default     = false
}

# --- Events and storage (same in every environment, see events.auto.tfvars) ----------------------

variable "topics" {
  description = "Producer service names; each gets the SNS topic <name>-events."
  type        = set(string)
}

variable "subscriptions" {
  description = "Consumer queues, keyed by queue name; each lists the topics it listens to. Empty event_types means no filtering."
  type = map(list(object({
    topic       = string
    event_types = optional(list(string), [])
  })))
}

variable "create_inspection_queues" {
  description = "A queue per topic that developers can read to see published events."
  type        = bool
  default     = false
}

variable "buckets" {
  description = "Application buckets, keyed by name without the prefix."
  type = map(object({
    versioning      = optional(bool, false)
    expiration_days = optional(number)
  }))
  default = {}
}

# --- Cluster nodes ------------------------------------------------------------------------------

variable "vpc_cidr" {
  description = "Must not overlap the pod (192.168.0.0/16) and service (10.96.0.0/12) ranges of the cluster."
  type        = string
  default     = "10.0.0.0/16"
}

variable "admin_cidrs" {
  description = "Addresses allowed to reach SSH (22) and the Kubernetes API (6443). Empty: only SSM Session Manager works."
  type        = list(string)
  default     = []
}

variable "ssh_public_key" {
  description = "Public key installed on the nodes. Null: no key pair, SSM Session Manager only."
  type        = string
  default     = null
}

variable "cp_instance_type" {
  description = "Control plane. c7i-flex.large (2 vCPU, 4 GiB) is free plan eligible."
  type        = string
  default     = "c7i-flex.large"
}

variable "worker_instance_type" {
  description = "Workers. m7i-flex.large (2 vCPU, 8 GiB) is free plan eligible."
  type        = string
  default     = "m7i-flex.large"
}

variable "worker_count" {
  type    = number
  default = 2
}

variable "node_volume_size" {
  description = "Root disk of each node in GiB (gp3): OS, container images and logs."
  type        = number
  default     = 30
}

variable "ingress_http_node_port" {
  description = "NodePort of Traefik's web entry point; the NLB forwards port 80 here."
  type        = number
  default     = 30080
}

variable "ingress_https_node_port" {
  description = "NodePort of Traefik's websecure entry point; the NLB forwards port 443 here."
  type        = number
  default     = 30443
}

# --- Workload identity --------------------------------------------------------------------------

variable "pod_identity" {
  description = "irsa: one IAM role per service account. node: every permission on the worker instance profile."
  type        = string
  default     = "irsa"

  validation {
    condition     = contains(["irsa", "node"], var.pod_identity)
    error_message = "pod_identity must be irsa or node."
  }
}

variable "k8s_namespace" {
  description = "Namespace of the application service accounts (IRSA trust policies match system:serviceaccount:<namespace>:<service>)."
  type        = string
  default     = "dershane"
}

variable "github_repo" {
  description = "owner/name allowed to push images to ECR from main. Null: no CI role."
  type        = string
  default     = null
}

variable "github_oidc_subject_prefix" {
  description = <<-EOT
    Start of the sub claim in GitHub's OIDC token, when the repository uses the immutable format
    (repo:<owner>@<owner id>/<repo>@<repo id>). Read it with
    gh api repos/<owner>/<repo>/actions/oidc/customization/sub (sub_claim_prefix).
    Null: the classic repo:<owner>/<repo>.
  EOT
  type        = string
  default     = null
}

# --- Data stores --------------------------------------------------------------------------------

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_allocated_storage" {
  description = "GiB."
  type        = number
  default     = 20
}

variable "db_skip_final_snapshot" {
  description = "false keeps a snapshot when the database is destroyed."
  type        = bool
  default     = true
}

variable "db_password_version" {
  description = "Increment to generate new service database passwords (write-only values are sent only when this changes)."
  type        = number
  default     = 1
}

variable "app_secret_version" {
  description = "Increment to generate new Keycloak and demo passwords (app-secrets.tf; sent only when this changes)."
  type        = number
  default     = 1
}

variable "cache_node_type" {
  type    = string
  default = "cache.t4g.micro"
}

variable "cache_auth_token_version" {
  description = "Increment to rotate the Redis AUTH token."
  type        = number
  default     = 1
}

variable "ecr_repositories" {
  type    = set(string)
  default = ["api-gateway", "user", "attendance", "task", "messaging", "exam-analysis", "notification", "file", "frontend", "keycloak"]
}

# --- Domain, e-mail, alerts ---------------------------------------------------------------------

variable "domain" {
  description = "Sender domain for SES. Its DNS stays with the registrar; the DKIM records are an output to copy there."
  type        = string
  default     = null
}

variable "ses_test_recipients" {
  description = "Addresses verified in SES so the sandbox account can send to them."
  type        = list(string)
  default     = []
}

variable "alert_email" {
  description = "Receives the CloudWatch alarm notifications (the subscription must be confirmed from the e-mail)."
  type        = string
  default     = null
}
