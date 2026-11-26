# Floci, applied by floci-init (local/docker-compose.yml) with AWS_ENDPOINT_URL=http://floci:4566.
# Only SNS topics, SQS queues (from events.auto.tfvars) and the optical files bucket; the state stays in
# the terraform-state volume. Everything else (EC2, RDS, NLB...) exists only on AWS.
environment              = "local"
emulator                 = true
create_inspection_queues = true

topics  = ["user", "attendance", "task", "exam-analysis", "messaging"]
buckets = ["exam-analysis-optical-files", "file-uploads"]

# Event lists follow the table in shared/events/README.md. A consumer that starts handling a new
# event type must add it here, otherwise SNS drops the event before it reaches the queue.
subscriptions = {
  "exam-analysis-user-events" = [{
    topic       = "user"
    event_types = ["UserUpdated", "StudentAddedToClassroom", "StudentRemovedFromClassroom"]
  }]
  "attendance-user-events" = [{
    topic       = "user"
    event_types = ["StudentAddedToClassroom", "StudentRemovedFromClassroom", "GuardianLinked", "GuardianUnlinked"]
  }]
  "notification-queue" = [
    {
      topic       = "user"
      event_types = ["UserUpdated", "GuardianLinked", "GuardianUnlinked"]
    },
    {
      topic = "exam-analysis"
    },
    {
      topic = "attendance"
    },
    {
      topic = "task"
    },
  ]
  "task-user-events" = [{
    topic       = "user"
    event_types = ["StudentAddedToClassroom", "StudentRemovedFromClassroom", "GuardianLinked", "GuardianUnlinked"]
  }]
  "messaging-user-events" = [{
    topic       = "user"
    event_types = ["UserUpdated"]
  }]
buckets = {
  "exam-analysis-optical-files" = {}
}
