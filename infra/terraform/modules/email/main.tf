# SES for the notification service, which sends over SMTP (Mailpit locally).
# The domain's DNS stays with the registrar: the three DKIM CNAMEs from the dkim_records output are
# written there once, after which SES marks the domain verified. A new account starts in the SES
# sandbox and can only send to verified addresses (test_recipients); leaving it is a console request.

data "aws_region" "current" {}

resource "aws_sesv2_email_identity" "domain" {
  email_identity = var.domain
}

resource "aws_sesv2_email_identity" "test_recipient" {
  for_each       = toset(var.test_recipients)
  email_identity = each.value
}

# SMTP needs a user name and password; SES derives them from an IAM access key. This is the only
# long-lived key in the project. Its secret is in the Terraform state, which is why the state bucket
# is private, encrypted and versioned.
resource "aws_iam_user" "smtp" {
  name = "${var.name}-ses-smtp"
}

resource "aws_iam_user_policy" "smtp" {
  name = "send-email"
  user = aws_iam_user.smtp.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["ses:SendRawEmail", "ses:SendEmail"]
      Resource = "*"
      Condition = {
        StringLike = { "ses:FromAddress" = "*@${var.domain}" }
      }
    }]
  })
}

resource "aws_iam_access_key" "smtp" {
  user = aws_iam_user.smtp.name
}

resource "aws_secretsmanager_secret" "smtp" {
  name                    = "${var.secret_prefix}/notification/smtp"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "smtp" {
  secret_id = aws_secretsmanager_secret.smtp.id

  secret_string = jsonencode({
    host     = local.smtp_host
    port     = 587
    starttls = true
    username = aws_iam_access_key.smtp.id
    password = aws_iam_access_key.smtp.ses_smtp_password_v4
  })
}

locals {
  smtp_host = "email-smtp.${data.aws_region.current.region}.amazonaws.com"
}
