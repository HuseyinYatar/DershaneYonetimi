variable "name" {
  type = string
}

variable "repositories" {
  type = set(string)
}

variable "keep_images" {
  type    = number
  default = 10
}

variable "force_delete" {
  description = "Lets terraform destroy delete repositories that still hold images."
  type        = bool
}

variable "github_repo" {
  description = "owner/name; null creates no CI role."
  type        = string
}

variable "github_branch" {
  type    = string
  default = "main"
}

variable "github_oidc_subject_prefix" {
  description = "Start of GitHub's OIDC sub claim (immutable format, with owner and repository ids). Null: repo:<github_repo>."
  type        = string
  default     = null
}
