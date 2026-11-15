output "dkim_records" {
  description = "CNAME records to add at the registrar so SES can verify the domain."
  value = [
    for token in try(aws_sesv2_email_identity.domain.dkim_signing_attributes[0].tokens, []) : {
      type  = "CNAME"
      name  = "${token}._domainkey.${var.domain}"
      value = "${token}.dkim.amazonses.com"
    }
  ]
}

output "smtp_host" {
  value = local.smtp_host
}

output "smtp_secret_arn" {
  value = aws_secretsmanager_secret.smtp.arn
}
