output "state_bucket" {
  description = "Goes into environments/prod.s3.tfbackend (bucket)."
  value       = aws_s3_bucket.state.bucket
}
