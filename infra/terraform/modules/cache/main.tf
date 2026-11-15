# Single-node Redis for the gateway's rate limit counters, messaging (Pub/Sub, tickets, presence) and
# the exam-analysis cache. TLS and an AUTH token are on because Pub/Sub frames carry message content.
# Clients: Java spring.data.redis.ssl.enabled=true + password; Python rediss://:<token>@host:6379/0.

# Ephemeral like the database passwords: the token reaches Redis and the secret through write-only
# arguments in the same apply and never enters the state. Increment auth_token_version to rotate.
ephemeral "random_password" "auth" {
  length  = 32
  special = false
}

resource "aws_elasticache_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.subnet_ids
}

resource "aws_elasticache_replication_group" "this" {
  replication_group_id = "${var.name}-redis"
  description          = "Rate limits, chat Pub/Sub and tickets, exam-analysis cache"

  engine               = "redis"
  engine_version       = var.engine_version
  node_type            = var.node_type
  num_cache_clusters   = 1
  port                 = 6379
  parameter_group_name = "default.redis7"

  subnet_group_name  = aws_elasticache_subnet_group.this.name
  security_group_ids = [var.security_group_id]

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  auth_token_wo              = ephemeral.random_password.auth.result
  auth_token_wo_version      = var.auth_token_version

  automatic_failover_enabled = false
  snapshot_retention_limit   = 0
  apply_immediately          = true
}

resource "aws_secretsmanager_secret" "auth" {
  name                    = "${var.secret_prefix}/redis"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "auth" {
  secret_id                = aws_secretsmanager_secret.auth.id
  secret_string_wo         = jsonencode({ password = ephemeral.random_password.auth.result })
  secret_string_wo_version = var.auth_token_version
}
