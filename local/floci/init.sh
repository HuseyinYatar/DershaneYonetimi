#!/bin/sh
# Creates the SNS topics, queues and buckets of the services, plus inspection queues for the topics.
# Will be replaced by Terraform (same resources on Floci and AWS) in the infrastructure weeks.
set -eu
until aws sns list-topics >/dev/null 2>&1; do
  echo "Waiting for Floci at ${AWS_ENDPOINT_URL}..."
  sleep 1
done

TOPIC_ARN=$(aws sns create-topic --name user-events --query TopicArn --output text)
QUEUE_URL=$(aws sqs create-queue --queue-name local-inspect-user-events --query QueueUrl --output text)
QUEUE_ARN=$(aws sqs get-queue-attributes --queue-url "$QUEUE_URL" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)
aws sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs \
  --notification-endpoint "$QUEUE_ARN" --attributes RawMessageDelivery=true >/dev/null

echo "Topic:  $TOPIC_ARN"
echo "Queue:  $QUEUE_URL (read it to see published events)"

# exam-analysis: bucket of the uploaded optical files, its own topic with an inspection queue, and
# the queue it will consume user events from (week 4). Every command is idempotent, so the script
# can run again on each `docker compose up`.
BUCKET=exam-analysis-optical-files
aws s3api head-bucket --bucket "$BUCKET" 2>/dev/null ||
  aws s3api create-bucket --bucket "$BUCKET" \
    --create-bucket-configuration LocationConstraint="$AWS_DEFAULT_REGION" >/dev/null

subscribe() { # topic_arn queue_name -> queue_url
  url=$(aws sqs create-queue --queue-name "$2" --query QueueUrl --output text)
  arn=$(aws sqs get-queue-attributes --queue-url "$url" \
    --attribute-names QueueArn --query Attributes.QueueArn --output text)
  aws sns subscribe --topic-arn "$1" --protocol sqs \
    --notification-endpoint "$arn" --attributes RawMessageDelivery=true >/dev/null
  echo "$url"
}

EXAM_TOPIC_ARN=$(aws sns create-topic --name exam-analysis-events --query TopicArn --output text)
EXAM_INSPECT_URL=$(subscribe "$EXAM_TOPIC_ARN" local-inspect-exam-analysis-events)
# No filter policy yet: the consumer skips event types it does not handle.
EXAM_USER_EVENTS_URL=$(subscribe "$TOPIC_ARN" exam-analysis-user-events)

echo "Bucket: $BUCKET"
echo "Topic:  $EXAM_TOPIC_ARN"
echo "Queue:  $EXAM_INSPECT_URL (read it to see published events)"
echo "Queue:  $EXAM_USER_EVENTS_URL (user events for exam-analysis)"

# notification: one queue for both topics, with a dead-letter queue. The service only reads and
# deletes messages; these subscriptions are what bring the events into its queue.
NOTIF_DLQ_URL=$(aws sqs create-queue --queue-name notification-queue-dlq --query QueueUrl --output text)
NOTIF_DLQ_ARN=$(aws sqs get-queue-attributes --queue-url "$NOTIF_DLQ_URL" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)

# VisibilityTimeout: a received message stays hidden this long; not deleted by then, it comes back.
# RedrivePolicy: after 5 receives without a delete (invalid or failing message) it moves to the DLQ.
cat > /tmp/notification-queue.json <<JSON
{"VisibilityTimeout": "60",
 "RedrivePolicy": "{\"deadLetterTargetArn\":\"$NOTIF_DLQ_ARN\",\"maxReceiveCount\":\"5\"}"}
JSON
NOTIF_URL=$(aws sqs create-queue --queue-name notification-queue \
  --attributes file:///tmp/notification-queue.json --query QueueUrl --output text)
NOTIF_ARN=$(aws sqs get-queue-attributes --queue-url "$NOTIF_URL" \
  --attribute-names QueueArn --query Attributes.QueueArn --output text)

# Subscription 1: user-events, filtered on the eventType message attribute. StudentAddedToClassroom
# and the other user events never reach this queue.
cat > /tmp/notification-user-subscription.json <<'JSON'
{"RawMessageDelivery": "true",
 "FilterPolicy": "{\"eventType\":[\"UserUpdated\",\"GuardianLinked\",\"GuardianUnlinked\"]}"}
JSON
aws sns subscribe --topic-arn "$TOPIC_ARN" --protocol sqs --notification-endpoint "$NOTIF_ARN" \
  --attributes file:///tmp/notification-user-subscription.json >/dev/null

# Subscription 2: exam-analysis-events, no filter (both of its events are needed).
aws sns subscribe --topic-arn "$EXAM_TOPIC_ARN" --protocol sqs --notification-endpoint "$NOTIF_ARN" \
  --attributes RawMessageDelivery=true >/dev/null

echo "Queue:  $NOTIF_URL (user-events [filtered] + exam-analysis-events for notification)"
echo "Queue:  $NOTIF_DLQ_URL (notification dead letters)"