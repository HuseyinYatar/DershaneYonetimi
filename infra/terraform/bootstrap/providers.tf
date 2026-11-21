# AWS only: bootstrap is applied once per account with an administrator SSO session.
provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "dershane"
      ManagedBy = "terraform-bootstrap"
    }
  }
}
