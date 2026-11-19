#!/usr/bin/env bash
# Replaces the placeholders in k8s/ with this AWS account's values, once, after the first terraform apply:
#   <AWS_ACCOUNT_ID>          from terraform output ecr_registry (<account>.dkr.ecr.<region>.amazonaws.com)
#   <ECR_REGISTRY>            terraform output ecr_registry
#   <RDS_ADDRESS>             terraform output rds_address
#   <REDIS_ADDRESS>           terraform output redis_address (needs standby = false)
#   <RDS_MASTER_SECRET_ARN>   terraform output rds_master_secret_arn
# Then review `git diff k8s/` and commit: Argo CD deploys what is on main. Image tags are not filled here:
# CI writes them when it pushes an image (.github/workflows/_image.yml).
#
#   k8s/fill-values.sh
# Needs an AWS session and an initialised infra/terraform (prod backend).
set -euo pipefail
cd "$(dirname "$0")"

out() { terraform -chdir=../infra/terraform output -raw "$1"; }

registry="$(out ecr_registry)"
account="${registry%%.*}"
rds="$(out rds_address)"
redis="$(out redis_address)"
master_secret="$(out rds_master_secret_arn)"

for value in "$registry" "$rds" "$redis" "$master_secret"; do
  if [ -z "$value" ] || [ "$value" = "null" ]; then
    echo "A Terraform output is empty; is the platform applied with standby = false?" >&2
    exit 1
  fi
done

# This script and the README describe the placeholders; only the manifests and values are filled.
files="$(grep -rlE --exclude=fill-values.sh --exclude=README.md \
  '<(AWS_ACCOUNT_ID|ECR_REGISTRY|RDS_ADDRESS|REDIS_ADDRESS|RDS_MASTER_SECRET_ARN)>' . || true)"
if [ -z "$files" ]; then
  echo "No placeholders left in k8s/."
  exit 0
fi

for file in $files; do
  sed -i.bak \
    -e "s|<AWS_ACCOUNT_ID>|${account}|g" \
    -e "s|<ECR_REGISTRY>|${registry}|g" \
    -e "s|<RDS_ADDRESS>|${rds}|g" \
    -e "s|<REDIS_ADDRESS>|${redis}|g" \
    -e "s|<RDS_MASTER_SECRET_ARN>|${master_secret}|g" \
    "$file"
  rm -f "$file.bak"
  echo "filled $file"
done
echo "Review with: git diff k8s/"
