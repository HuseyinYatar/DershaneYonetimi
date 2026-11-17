variable "name" {
  type = string
}

variable "nodes" {
  description = "Keyed by host name (cp, w1, w2...)."
  type = map(object({
    role          = string
    instance_type = string
    private_ip    = string
  }))
}

variable "control_plane_key" {
  type    = string
  default = "cp"
}

variable "subnet_id" {
  type = string
}

variable "security_group_id" {
  type = string
}

variable "control_plane_instance_profile" {
  type = string
}

variable "worker_instance_profile" {
  type = string
}

variable "ssh_public_key" {
  type = string
}

variable "volume_size" {
  type = number
}

variable "imds_hop_limit" {
  type = number
}

variable "kubernetes_version" {
  type = string
}

variable "ecr_credential_provider_version" {
  type = string
}

variable "secret_prefix" {
  type = string
}
