# Internal addresses of the data stores, for the cluster. They are not passwords (RDS and Redis sit in
# private subnets, only the nodes' security group may connect, Redis also needs TLS and its AUTH token),
# but they are internal details and the repository is public, so k8s/ does not contain them: External
# Secrets reads this entry and builds DB_URL, REDIS_HOST, DATABASE_URL, REDIS_URL, PGHOST and KC_DB_URL
# from it (k8s/environments/prod, k8s/platform/db-init, bootstrap, keycloak).
#
# Not write-only like the passwords: the values come from the resources themselves and are already in the
# state. They follow the resources: a recreated database or cache updates the entry on the next apply.

resource "aws_secretsmanager_secret" "endpoints" {
  count                   = local.platform
  name                    = "${local.secret_prefix}/endpoints"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "endpoints" {
  count     = local.platform
  secret_id = aws_secretsmanager_secret.endpoints[0].id

  # standby = true removes the cache; the entry then carries an empty Redis address.
  secret_string = jsonencode({
    rds_host   = module.database[0].address
    redis_host = try(module.cache[0].address, "")
  })
}
