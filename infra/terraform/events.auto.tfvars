# Event wiring, the same in every environment (loaded automatically, no -var-file needed).
# Event lists follow the table in shared/events/README.md. A consumer that starts handling a new
# event type must add it here, otherwise SNS drops the event before it reaches the queue. A new
# queue or topic also needs its IAM entry in local.service_access (main.tf).

topics = ["user", "attendance", "task", "exam-analysis", "messaging"]

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
}
