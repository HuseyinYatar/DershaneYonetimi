variable "name" {
  type = string
}

variable "subnet_ids" {
  type = list(string)
}

variable "security_group_id" {
  type = string
}

variable "engine_major_version" {
  description = "Same major as the local postgres:17 image."
  type        = string
  default     = "17"
}

variable "instance_class" {
  type = string
}

variable "allocated_storage" {
  type = number
}

variable "backup_retention_days" {
  description = "Free plan accounts may reject longer retention."
  type        = number
  default     = 1
}

variable "skip_final_snapshot" {
  type = bool
}

variable "databases" {
  description = "Database name = role name; one secret each."
  type        = set(string)
}

variable "secret_prefix" {
  type = string
}

variable "password_version" {
  type = number
}
