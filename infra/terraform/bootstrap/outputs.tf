output "state_bucket" {
  description = "Goes into environments/prod.s3.tfbackend (bucket) and the TF_STATE_BUCKET repository variable."
  value       = aws_s3_bucket.state.bucket
}

output "terraform_plan_role_arn" {
  description = "Repository variable AWS_TERRAFORM_PLAN_ROLE_ARN."
  value       = one(aws_iam_role.terraform_plan[*].arn)
}

output "terraform_apply_role_arn" {
  description = "Repository variable AWS_TERRAFORM_APPLY_ROLE_ARN."
  value       = one(aws_iam_role.terraform_apply[*].arn)
}
