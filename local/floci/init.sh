#!/bin/sh
# Creates the SNS topic of the user service and an inspection queue subscribed to it.
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
