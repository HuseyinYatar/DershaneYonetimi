# Passwords the cluster needs besides the database, Redis and SMTP ones (those are written by their modules).
# External Secrets copies them into the pods (k8s/platform/keycloak, bootstrap, demo-seed):
#   keycloak/admin                Keycloak's own admin console (master realm), user "admin"
#   keycloak/user-service-client  client secret the user service uses for the Keycloak Admin API
#   keycloak/superadmin           first password of superadmin@onlinecourses.site (changed at first login)
#   demo/password                 shared password of every demo teacher, student and guardian (k8s demo-seed)
# Like the database passwords they are ephemeral: generated during the apply and sent through a write-only
# argument, so neither the plan nor the state holds them. Read one with
#   aws secretsmanager get-secret-value --secret-id dershane/demo/password --query SecretString --output text

locals {
  app_secrets = var.platform_enabled ? toset([
    "keycloak/admin",
    "keycloak/user-service-client",
    "keycloak/superadmin",
    "demo/password",
  ]) : toset([])
}

ephemeral "random_password" "app" {
  for_each = local.app_secrets
  length   = 24
  special  = false
}

resource "aws_secretsmanager_secret" "app" {
  for_each                = local.app_secrets
  name                    = "${local.secret_prefix}/${each.key}"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "app" {
  for_each  = local.app_secrets
  secret_id = aws_secretsmanager_secret.app[each.key].id

  # Every entry is {"value": "..."}; the ExternalSecrets read the "value" property.
  secret_string_wo         = jsonencode({ value = ephemeral.random_password.app[each.key].result })
  secret_string_wo_version = var.app_secret_version
}

output "app_secret_names" {
  description = "Secrets Manager names of the Keycloak and demo passwords."
  value       = [for secret in aws_secretsmanager_secret.app : secret.name]
}
