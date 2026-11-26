variable "name" {
  type = string
}

variable "vpc_cidr" {
  type = string
}

variable "admin_cidrs" {
  type = list(string)
}

variable "ingress_http_node_port" {
  type = number
}

variable "ingress_https_node_port" {
  type = number
}
