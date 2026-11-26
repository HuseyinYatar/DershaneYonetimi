# One PostgreSQL instance holding one database and role per service, the same eight as
# local/postgres/init.sql. No service is granted another service's database.
#
# Terraform cannot reach the instance (private subnets, no bastion), so it does not create the
# databases. It writes one secret per service and the master login (dershane/db/master); a Kubernetes
# Job (k8s/, db-init) reads them and runs the CREATE ROLE / CREATE DATABASE statements idempotently.

resource "aws_db_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.subnet_ids
}

resource "aws_db_parameter_group" "this" {
  name   = var.name
  family = "postgres${var.engine_major_version}"

  # Clients must use TLS (JDBC sslmode=require, psycopg sslmode=require).
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
}

resource "aws_db_instance" "this" {
  identifier     = "${var.name}-postgres"
  engine         = "postgres"
  engine_version = var.engine_major_version
  instance_class = var.instance_class

  allocated_storage = var.allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.security_group_id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false
  multi_az               = false

  # Terraform generates the master password and keeps it in dershane/db/master (below), like the service
  # passwords: write-only, so neither the plan nor the state contains it. Not the RDS-managed secret:
  # that one gets a random name (rds!db-...), which k8s/ would have to carry.
  username            = "dershane_admin"
  password_wo         = ephemeral.random_password.master.result
  password_wo_version = var.password_version

  backup_retention_period    = var.backup_retention_days
  skip_final_snapshot        = var.skip_final_snapshot
  final_snapshot_identifier  = var.skip_final_snapshot ? null : "${var.name}-postgres-final"
  copy_tags_to_snapshot      = true
  deletion_protection        = false
  auto_minor_version_upgrade = true
  apply_immediately          = true
}

# --- Master credentials (db-init only) -----------------------------------------------------------

ephemeral "random_password" "master" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "master" {
  name                    = "${var.secret_prefix}/db/master"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "master" {
  secret_id = aws_secretsmanager_secret.master.id

  secret_string_wo = jsonencode({
    username = aws_db_instance.this.username
    password = ephemeral.random_password.master.result
  })
  secret_string_wo_version = var.password_version
}

# --- Service credentials ------------------------------------------------------------------------

# Ephemeral: the password exists only during the apply and is sent through a write-only argument, so
# neither the plan nor the state contains it. It is sent again only when password_version changes.
ephemeral "random_password" "service" {
  for_each = var.databases
  length   = 32
  special  = false
}

resource "aws_secretsmanager_secret" "service" {
  for_each                = var.databases
  name                    = "${var.secret_prefix}/db/${each.key}"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "service" {
  for_each  = var.databases
  secret_id = aws_secretsmanager_secret.service[each.key].id

  secret_string_wo = jsonencode({
    username = each.key
    password = ephemeral.random_password.service[each.key].result
    dbname   = each.key
  })
  secret_string_wo_version = var.password_version
}
