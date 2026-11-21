# Runs against real AWS (prod) and, for SNS, SQS and S3 only, against Floci (local, emulator = true).
# The address is never written here: the provider reads AWS_ENDPOINT_URL (set to Floci by floci-init,
# unset against AWS), like the services. Credentials come from the environment (SSO session, GitHub
# OIDC or Floci's test keys), never from code.
provider "aws" {
  region = var.aws_region

  skip_credentials_validation = var.emulator
  skip_metadata_api_check     = var.emulator
  skip_requesting_account_id  = var.emulator
  s3_use_path_style           = var.emulator

  default_tags {
    tags = {
      Project     = "dershane"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
