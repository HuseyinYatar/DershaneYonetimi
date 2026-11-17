# Values the services' Kubernetes configuration needs (k8s/environments/<env>/). Platform outputs are
# null while platform_enabled = false.

output "topic_arns" {
  value = module.events.topic_arns
}

output "queue_urls" {
  value = module.events.queue_urls
}

output "bucket_names" {
  value = module.storage.bucket_names
}

# --- Cluster ------------------------------------------------------------------------------------

output "control_plane_public_ip" {
  description = "kubectl and SSH; add it to the API server certificate (--apiserver-cert-extra-sans)."
  value       = one(module.nodes[*].control_plane_public_ip)
}

output "control_plane_private_ip" {
  description = "--apiserver-advertise-address and the kubeadm join endpoint."
  value       = one(module.nodes[*].control_plane_private_ip)
}

output "nodes" {
  value = one(module.nodes[*].nodes)
}

output "ansible_inventory" {
  value = one(module.nodes[*].ansible_inventory)
}

output "cluster_secret_arns" {
  value = one(module.nodes[*].cluster_secret_arns)
}

output "oidc_issuer" {
  description = "kube-apiserver --service-account-issuer (and <issuer>/keys.json as --service-account-jwks-uri)."
  value       = one(module.iam[*].oidc_issuer)
}

output "oidc_bucket" {
  description = "Upload keys.json here after kubeadm init."
  value       = one(module.iam[*].oidc_bucket)
}

output "service_role_arns" {
  description = "AWS_ROLE_ARN of each service account."
  value       = one(module.iam[*].service_role_arns)
}

# --- Entry and DNS ------------------------------------------------------------------------------

output "ingress_public_ip" {
  description = "A records for @ and * of the domain at the registrar."
  value       = one(module.edge[*].public_ip)
}

output "ses_dkim_records" {
  description = "CNAME records to add at the registrar."
  value       = one(module.email[*].dkim_records)
}

# --- Data stores and images ---------------------------------------------------------------------

output "rds_address" {
  value = one(module.database[*].address)
}

output "rds_port" {
  value = one(module.database[*].port)
}

output "rds_master_secret_arn" {
  value = one(module.database[*].master_secret_arn)
}

output "db_secret_arns" {
  value = one(module.database[*].service_secret_arns)
}

output "redis_address" {
  value = one(module.cache[*].address)
}

output "redis_auth_secret_arn" {
  value = one(module.cache[*].auth_secret_arn)
}

output "smtp_host" {
  value = one(module.email[*].smtp_host)
}

output "smtp_secret_arn" {
  value = one(module.email[*].smtp_secret_arn)
}

output "ecr_registry" {
  value = one(module.registry[*].registry)
}

output "ecr_push_role_arn" {
  description = "Role for the image workflow (aws-actions/configure-aws-credentials)."
  value       = one(module.registry[*].github_push_role_arn)
}

output "alerts_topic_arn" {
  value = one(module.monitoring[*].alerts_topic_arn)
}
