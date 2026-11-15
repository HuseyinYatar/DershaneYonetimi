output "address" {
  value = aws_db_instance.this.address
}

output "port" {
  value = aws_db_instance.this.port
}

output "master_secret_arn" {
  description = "Created and rotated by RDS; read by the db-init Job only."
  value       = aws_db_instance.this.master_user_secret[0].secret_arn
}

output "service_secret_arns" {
  value = { for name, secret in aws_secretsmanager_secret.service : name => secret.arn }
}
