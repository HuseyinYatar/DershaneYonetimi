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
