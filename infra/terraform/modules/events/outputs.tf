output "topic_arns" {
  value = { for name, topic in aws_sns_topic.this : name => topic.arn }
}

output "queue_urls" {
  value = { for name, queue in aws_sqs_queue.this : name => queue.url }
}
