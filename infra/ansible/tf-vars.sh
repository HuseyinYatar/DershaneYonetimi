#!/usr/bin/env bash
# Copies the Terraform outputs the playbook needs into group_vars/all/terraform.json. Only these fields
# are written: `terraform output -json` would also print sensitive outputs in clear text.
# Needs an AWS session and an initialised infra/terraform (prod backend).
set -euo pipefail
cd "$(dirname "$0")"

terraform -chdir=../terraform output -json | jq '{
  control_plane_public_ip: .control_plane_public_ip.value,
  oidc_issuer:             .oidc_issuer.value,
  oidc_bucket:             .oidc_bucket.value,
  etcd_backup_bucket:      .bucket_names.value["etcd-backups"]
}' > group_vars/all/terraform.json

if jq -e 'to_entries | any(.value == null)' group_vars/all/terraform.json > /dev/null; then
  echo "Some outputs are empty; was the platform applied (platform_enabled = true)?" >&2
  cat group_vars/all/terraform.json >&2
  exit 1
fi
echo "group_vars/all/terraform.json written:"
cat group_vars/all/terraform.json
