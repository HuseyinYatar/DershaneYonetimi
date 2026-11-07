variable "topics" {
  type = set(string)
}

variable "subscriptions" {
  type = map(list(object({
    topic       = string
    event_types = optional(list(string), [])
  })))
}

variable "create_inspection_queues" {
  type = bool
}
