variable "buckets" {
  description = "Keyed by bucket name without the prefix."
  type = map(object({
    versioning      = optional(bool, false)
    expiration_days = optional(number)
  }))
}

variable "bucket_prefix" {
  type = string
}

variable "force_destroy" {
  type = bool
}
