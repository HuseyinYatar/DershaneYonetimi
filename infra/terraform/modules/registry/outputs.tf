output "registry" {
  description = "docker login / image prefix: <registry>/<service>:<commit sha>."
  value       = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${data.aws_region.current.region}.amazonaws.com"
}

output "repository_urls" {
  value = { for name, repo in aws_ecr_repository.this : name => repo.repository_url }
}

output "github_push_role_arn" {
  value = one(aws_iam_role.github_push[*].arn)
}
