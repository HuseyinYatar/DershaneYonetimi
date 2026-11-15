output "worker_instance_profile" {
  value = aws_iam_instance_profile.node["worker"].name
}

output "control_plane_instance_profile" {
  value = aws_iam_instance_profile.node["control-plane"].name
}

output "imds_hop_limit" {
  description = "1 with IRSA so containers cannot read the node role from instance metadata."
  value       = local.irsa ? 1 : 2
}

output "service_role_arns" {
  description = "AWS_ROLE_ARN per service account (empty with pod_identity = node)."
  value       = { for key, role in aws_iam_role.service : key => role.arn }
}

output "oidc_issuer" {
  value = local.irsa ? local.oidc_issuer : null
}

output "oidc_bucket" {
  value = local.irsa ? aws_s3_bucket.oidc[0].bucket : null
}
