aws_endpoint_url         = "http://floci:4566"
emulator                 = true
create_inspection_queues = true

topics  = ["user", "attendance", "task", "exam-analysis"]
buckets = ["exam-analysis-optical-files"]

# Event lists follow the table in shared/events/README.md. A consumer that starts handling a new
# event type must add it here, otherwise SNS drops the event before it reaches the queue.
subscriptions = {
  "exam-analysis-user-events" = {
    topic       = "user"
    event_types = ["StudentAddedToClassroom", "UserUpdated"]
  }
  "attendance-user-events" = {
    topic       = "user"
    event_types = ["StudentAddedToClassroom", "StudentRemovedFromClassroom", "GuardianLinked", "GuardianUnlinked"]
  }
  "task-user-events" = {
    topic       = "user"
    event_types = ["StudentAddedToClassroom", "StudentRemovedFromClassroom", "GuardianLinked", "GuardianUnlinked"]
  }
}
