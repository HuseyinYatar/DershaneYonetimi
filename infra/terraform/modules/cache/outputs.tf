output "address" {
  value = aws_elasticache_replication_group.this.primary_endpoint_address
}

output "port" {
  value = aws_elasticache_replication_group.this.port
}

output "auth_secret_arn" {
  value = aws_secretsmanager_secret.auth.arn
}
