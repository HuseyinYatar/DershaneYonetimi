variable "name" {
  type = string
}

variable "alert_email" {
  type = string
}

variable "dlq_names" {
  type = list(string)
}
