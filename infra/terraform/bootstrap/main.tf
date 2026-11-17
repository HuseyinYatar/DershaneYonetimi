# Applied once per account, by hand, with local state (bootstrap/terraform.tfstate, gitignored).
# It creates what the main configuration needs before it can run at all:
#   - the S3 bucket that holds the main configuration's state (it cannot create its own bucket)
#   - a monthly budget alert, before anything costs money
#   - GitHub Actions OIDC and the roles the Terraform workflow assumes (no AWS keys in GitHub)

data "aws_caller_identity" "current" {}

locals {
  state_bucket = "dershane-tfstate-${data.aws_caller_identity.current.account_id}"
  oidc_host    = "token.actions.githubusercontent.com" #GitHub Actions'ın token ürettiği resmi sunucudur
}

# --- State bucket -------------------------------------------------------------------------------

resource "aws_s3_bucket" "state" {
  bucket = local.state_bucket

  lifecycle {
    prevent_destroy = true
  }
}

# Every state write is a new version: a broken state can be restored from the previous one.
resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket = aws_s3_bucket.state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# The state holds secrets (the SES SMTP password): never send it over plain HTTP.
resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "DenyPlainHttp"
      Effect    = "Deny"
      Principal = "*"
      Action    = "s3:*"
      Resource  = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"]
      Condition = { Bool = { "aws:SecureTransport" = "false" } }
    }]
  })

  depends_on = [aws_s3_bucket_public_access_block.state]
}

# --- Budget -------------------------------------------------------------------------------------

# Free plan usage is paid from credits, so credits are excluded: the alert tracks what the resources
# cost, not the (zero) invoice. Setting up a budget also earns free plan credit.
resource "aws_budgets_budget" "monthly" {
  count        = var.budget_limit_usd == null ? 0 : 1
  name         = "dershane-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.budget_limit_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  cost_types {
    include_credit = false
    include_refund = false
  }

  dynamic "notification" {
    for_each = [50, 80, 100]
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.alert_email]
    }
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.alert_email]
  }
}

# --- GitHub Actions -----------------------------------------------------------------------------

# Lets GitHub Actions exchange its job token for temporary AWS keys. Only the ECR push role
# (modules/registry, main branch) trusts it; Terraform itself is planned and applied by hand from a
# team member's own IAM user, never from CI.
resource "aws_iam_openid_connect_provider" "github" {
  count          = var.github_repo == null ? 0 : 1
  url            = "https://${local.oidc_host}"
  client_id_list = ["sts.amazonaws.com"]
}
