locals {
  inspection = var.create_inspection_queues ? {
    for topic in var.topics : "local-inspect-${topic}-events" => [{ topic = topic, event_types = [] }]
  } : {}

  # Consumer queues and inspection queues share one shape, so queue policy and subscriptions are
  # written once. A queue can listen to several topics.
  queues = merge(var.subscriptions, local.inspection)

  links = merge([
    for queue, sources in local.queues : {
      for s in sources : "${queue}/${s.topic}" => merge(s, { queue = queue })
    }
  ]...)
}

resource "aws_sns_topic" "this" {
  for_each = var.topics
  name     = "${each.value}-events"
}

resource "aws_sqs_queue" "dlq" {
  for_each                  = var.subscriptions
  name                      = "${each.key}-dlq"
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue" "this" {
  for_each = local.queues
  name     = each.key

  # A received message stays hidden for 60 s; not deleted by then, it comes back.
  visibility_timeout_seconds = contains(keys(var.subscriptions), each.key) ? 60 : 30

  redrive_policy = contains(keys(var.subscriptions), each.key) ? jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    maxReceiveCount     = 5
  }) : null
}

resource "aws_sqs_queue_policy" "this" {
  for_each  = local.queues
  queue_url = aws_sqs_queue.this[each.key].url

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.this[each.key].arn
      Condition = { ArnEquals = { "aws:SourceArn" = [for s in each.value : aws_sns_topic.this[s.topic].arn] } }
    }]
  })
}

resource "aws_sns_topic_subscription" "this" {
  for_each             = local.links
  topic_arn            = aws_sns_topic.this[each.value.topic].arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.this[each.value.queue].arn
  raw_message_delivery = true
  filter_policy = length(each.value.event_types) > 0 ? jsonencode({
    eventType = each.value.event_types
  }) : null

  depends_on = [aws_sqs_queue_policy.this]
}
