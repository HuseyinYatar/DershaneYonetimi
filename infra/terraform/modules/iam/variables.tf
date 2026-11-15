variable "name" {
  type = string
}

variable "pod_identity" {
  type = string
}

variable "k8s_namespace" {
  type = string
}

variable "secret_prefix" {
  type = string
}

variable "service_access" {
  description = "Service name => topics it publishes to, queues it reads, buckets it uses (keys of the maps below)."
  type = map(object({
    publish = optional(list(string), [])
    consume = optional(list(string), [])
    buckets = optional(list(string), [])
  }))
}

variable "topic_arns" {
  type = map(string)
}

variable "queue_arns" {
  type = map(string)
}

variable "bucket_arns" {
  type = map(string)
}

variable "etcd_bucket_arn" {
  type = string
}

variable "extra_secret_arns" {
  description = "Secrets outside the prefix that External Secrets may read (the RDS-managed master password)."
  type        = list(string)
  default     = []
}
