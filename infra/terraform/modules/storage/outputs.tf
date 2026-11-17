output "bucket_names" {
  value = { for key, bucket in aws_s3_bucket.this : key => bucket.bucket }
}

output "bucket_arns" {
  value = { for key, bucket in aws_s3_bucket.this : key => bucket.arn }
}
