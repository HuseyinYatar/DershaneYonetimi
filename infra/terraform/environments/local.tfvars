# Floci, applied by floci-init (local/docker-compose.yml) with AWS_ENDPOINT_URL=http://floci:4566.
# Only SNS topics, SQS queues (from events.auto.tfvars) and the optical files bucket; the state stays in
# the terraform-state volume. Everything else (EC2, RDS, NLB...) exists only on AWS.
environment              = "local"
emulator                 = true
create_inspection_queues = true

buckets = {
  "exam-analysis-optical-files" = {}
}
