variable "name" {
  type = string
}

variable "enabled" {
  description = "false (standby) removes the NLB; the Elastic IP stays."
  type        = bool
}

variable "vpc_id" {
  type = string
}

variable "subnet_id" {
  type = string
}

variable "security_group_id" {
  type = string
}

variable "target_instance_ids" {
  description = "Workers that run Traefik's NodePorts."
  type        = list(string)
}

variable "http_node_port" {
  type = number
}

variable "https_node_port" {
  type = number
}
