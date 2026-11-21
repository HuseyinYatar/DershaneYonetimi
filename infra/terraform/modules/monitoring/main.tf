# Alarms that must reach a person. A message in a dead letter queue is an event a consumer failed to
# apply five times; it waits there until it is redriven (see shared/events/README.md).
# Log-based alerts (the gateway's Redis rate limiter errors) belong to Grafana, not CloudWatch.

resource "aws_sns_topic" "alerts" {
  name = "${var.name}-alerts"
}

# AWS sends a confirmation e-mail; alarms are delivered only after the link in it is clicked.
resource "aws_sns_topic_subscription" "email" {
  count     = var.alert_email == null ? 0 : 1
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = var.alert_email
}

resource "aws_cloudwatch_metric_alarm" "dlq" {
  for_each = toset(var.dlq_names)

  alarm_name        = "${var.name}-${each.value}-not-empty"
  alarm_description = "Messages in ${each.value}: a consumer could not apply them. Fix the cause, then redrive."

  namespace   = "AWS/SQS"
  metric_name = "ApproximateNumberOfMessagesVisible"
  dimensions  = { QueueName = each.value }
  statistic   = "Maximum"
  period      = 300

  evaluation_periods  = 1
  comparison_operator = "GreaterThanThreshold"
  threshold           = 0
  treat_missing_data  = "notBreaching"

  alarm_actions = [aws_sns_topic.alerts.arn]
  ok_actions    = [aws_sns_topic.alerts.arn]
}
