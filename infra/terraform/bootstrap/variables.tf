variable "aws_region" {
  type    = string
  default = "eu-central-1"
}

variable "budget_limit_usd" {
  description = "Monthly cost alert threshold. Null: no budget."
  type        = number
  default     = null
}

variable "alert_email" {
  description = "Receives the budget alerts. Set with TF_VAR_alert_email, not in a committed file."
  type        = string
  default     = null

  validation {
    condition     = var.budget_limit_usd == null || var.alert_email != null
    error_message = "A budget needs alert_email (TF_VAR_alert_email)."
  }
}

variable "github_repo" {
  description = "owner/name trusted by the Terraform roles. Null: no GitHub OIDC."
  type        = string
  default     = null
}
