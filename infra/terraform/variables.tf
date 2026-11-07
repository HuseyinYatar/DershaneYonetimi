variable "aws_region" {
  type    = string
  default = "eu-central-1"
}

variable "aws_endpoint_url" {
  description = "Floci address locally; null against real AWS."
  type        = string
  default     = null
}

variable "emulator" {
  description = "Skips the AWS account and credential checks an emulator cannot answer."
  type        = bool
  default     = false
}

variable "topics" {
  description = "Producer service names; each gets the SNS topic <name>-events."
  type        = set(string)
}

variable "subscriptions" {
  description = "Consumer queues, keyed by queue name; each lists the topics it listens to. Empty event_types means no filtering."
  type = map(list(object({
    topic       = string
    event_types = optional(list(string), [])
  })))
}

variable "buckets" {
  type    = set(string)
  default = []
}

variable "create_inspection_queues" {
  description = "A queue per topic that developers can read to see published events."
  type        = bool
  default     = false
}
