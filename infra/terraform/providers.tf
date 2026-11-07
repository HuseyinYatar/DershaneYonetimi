# The same code runs against Floci (aws_endpoint_url set, emulator = true) and real AWS (both unset).
# Credentials come from the environment (AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY), never from code.
provider "aws" {
  region = var.aws_region

  skip_credentials_validation = var.emulator
  skip_metadata_api_check     = var.emulator
  skip_requesting_account_id  = var.emulator
  s3_use_path_style           = var.emulator

  endpoints {
    sns = var.aws_endpoint_url
    sqs = var.aws_endpoint_url
    s3  = var.aws_endpoint_url
  }
}
