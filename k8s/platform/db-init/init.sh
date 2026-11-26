#!/bin/sh
# For every service: a login role with the password from Secrets Manager and a database it owns.
# Safe to run again: existing roles only get their password refreshed, existing databases are kept.
# The database and the role share the name, as in local/postgres/init.sql. The list matches
# infra/terraform/main.tf (local.databases).
set -eu

for db in user_service attendance_service task_service messaging_service exam_analysis notification file_service keycloak; do
  password="$(printenv "$db")"
  echo "db-init: $db"
  psql -v ON_ERROR_STOP=1 -v name="$db" -v password="$password" -d postgres <<'SQL'
SELECT format('CREATE ROLE %I LOGIN', :'name')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'name')\gexec
SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'name', :'password')\gexec
-- The RDS master user is not a superuser: it must be a member of the role to make it a database owner.
SELECT format('GRANT %I TO CURRENT_USER', :'name')\gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'name', :'name')
 WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'name')\gexec
SQL
done
echo "db-init: done"
