variable "name" {
  type = string
}

variable "subnet_ids" {
  type = list(string)
}

variable "security_group_id" {
  type = string
}

variable "node_type" {
  type = string
}

variable "engine_version" {
  description = "Redis OSS; the local image is redis:7.4."
  type        = string
  default     = "7.1"
}

variable "secret_prefix" {
  type = string
}

variable "auth_token_version" {
  type = number
}
