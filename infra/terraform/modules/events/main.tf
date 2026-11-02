locals {
  inspection = var.create_inspection_queues ? {
    for topic in var.topics : "local-inspect-${topic}-events" => {
      topic       = topic
      event_types = []
      dlq         = false
    }
  } : {}

  # Consumer queues and inspection queues share one shape, so queue policy and subscription are
  # written once.
  queues = merge(
    { for name, s in var.subscriptions : name => merge(s, { dlq = true }) },
    local.inspection,
  )
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

  redrive_policy = each.value.dlq ? jsonencode({
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
      Condition = { ArnEquals = { "aws:SourceArn" = aws_sns_topic.this[each.value.topic].arn } }
    }]
  })
}

resource "aws_sns_topic_subscription" "this" {
  for_each             = local.queues
  topic_arn            = aws_sns_topic.this[each.value.topic].arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.this[each.key].arn
  raw_message_delivery = true
  filter_policy = length(each.value.event_types) > 0 ? jsonencode({
    eventType = each.value.event_types
  }) : null

  depends_on = [aws_sqs_queue_policy.this]
}
