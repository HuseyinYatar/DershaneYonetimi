#!/usr/bin/env bash
# Starts or stops the cluster nodes and the database between work sessions, to save free plan credit.
# Terraform does not track whether an instance runs, so a later apply does not undo this.
# The NLB and ElastiCache keep running; remove them for long breaks with standby = true.
#
#   infra/scripts/power.sh on  [environment]    database first, then the nodes
#   infra/scripts/power.sh off [environment]    nodes first, then the database
#
# Needs an AWS CLI session for the account; how to sign in is in infra/terraform/README.md
# ("Laptop'ta giriş"). RDS starts a stopped database by itself after 7 days; run "off" again if the
# break is longer.
set -euo pipefail

ACTION="${1:-}"
ENVIRONMENT="${2:-prod}"
DB_ID="dershane-$ENVIRONMENT-postgres"

if [[ "$ACTION" != "on" && "$ACTION" != "off" ]]; then
  echo "usage: $0 on|off [environment]" >&2
  exit 2
fi

instance_ids() {
  aws ec2 describe-instances \
    --filters "Name=tag:Project,Values=dershane" "Name=tag:Environment,Values=$ENVIRONMENT" \
      "Name=tag:Role,Values=control-plane,worker" \
      "Name=instance-state-name,Values=pending,running,stopping,stopped" \
    --query 'Reservations[].Instances[].InstanceId' --output text
}

db_status() {
  aws rds describe-db-instances --db-instance-identifier "$DB_ID" \
    --query 'DBInstances[0].DBInstanceStatus' --output text
}

IDS="$(instance_ids)"
if [[ -z "$IDS" ]]; then
  echo "power: no nodes tagged Environment=$ENVIRONMENT" >&2
  exit 1
fi

if [[ "$ACTION" == "on" ]]; then
  if [[ "$(db_status)" == "stopped" ]]; then
    aws rds start-db-instance --db-instance-identifier "$DB_ID" >/dev/null
    echo "power: starting $DB_ID"
    aws rds wait db-instance-available --db-instance-identifier "$DB_ID"
  fi
  # shellcheck disable=SC2086 # IDS is a space-separated list on purpose
  aws ec2 start-instances --instance-ids $IDS >/dev/null
  # shellcheck disable=SC2086
  aws ec2 wait instance-running --instance-ids $IDS
  echo "power: nodes running ($IDS). Worker public IPs changed; the control plane keeps its Elastic IP."
else
  # shellcheck disable=SC2086
  aws ec2 stop-instances --instance-ids $IDS >/dev/null
  # shellcheck disable=SC2086
  aws ec2 wait instance-stopped --instance-ids $IDS
  echo "power: nodes stopped ($IDS)"
  if [[ "$(db_status)" == "available" ]]; then
    aws rds stop-db-instance --db-instance-identifier "$DB_ID" >/dev/null
    echo "power: stopping $DB_ID (RDS starts it again after 7 days)"
  fi
fi
