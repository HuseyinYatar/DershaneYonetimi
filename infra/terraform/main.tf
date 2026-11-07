module "events" {
  source = "./modules/events"

  topics                   = var.topics
  subscriptions            = var.subscriptions
  create_inspection_queues = var.create_inspection_queues
}

resource "aws_s3_bucket" "this" {
  for_each = var.buckets
  bucket   = each.value
}
