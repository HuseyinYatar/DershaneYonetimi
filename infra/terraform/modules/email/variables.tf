variable "name" {
  type = string
}

variable "domain" {
  type = string
}

variable "test_recipients" {
  type = list(string)
}

variable "secret_prefix" {
  type = string
}
