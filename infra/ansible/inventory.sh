#!/usr/bin/env bash
# Writes inventory.ini from Terraform's ansible_inventory output (cp, w1, w2 with their public and private
# IPs). Run it at the start of every session: worker public IPs change whenever the instances are
# stopped and started. Also forgets the host keys of the previous session (see ansible.cfg).
# Needs an AWS session and an initialised infra/terraform (prod backend).
set -euo pipefail
cd "$(dirname "$0")"

terraform -chdir=../terraform output -raw ansible_inventory > inventory.ini
: > .ssh_known_hosts
echo "inventory.ini written:"
cat inventory.ini
