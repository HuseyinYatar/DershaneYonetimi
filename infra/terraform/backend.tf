# AWS: partial configuration, bucket/key/region come from environments/prod.s3.tfbackend:
#   terraform init -backend-config=environments/prod.s3.tfbackend
# The state bucket is created by bootstrap/. Locally (Floci) floci-init replaces this block with a
# local backend through backend_override.tf, so the state stays in a Docker volume.
terraform {
  backend "s3" {}
}
