# Event contracts

JSON Schemas (draft 2020-12) of the events exchanged between services. Producers and consumers in
Java and Python validate against these files. Producers have contract tests that fail when their
payloads drift from the schema.

## Layout

```
shared/events/
├── envelope.schema.json          wrapper used by every event
└── <producer>/<EventType>.schema.json   schema of the "data" field
```

## Envelope

```json
{
  "eventId": "0f0a6f0e-5b1e-4d0c-9a51-0d1c2b3a4f5e",
  "eventType": "StudentAddedToClassroom",
  "schemaVersion": 1,
  "occurredAt": "2026-10-17T09:30:00.123Z",
  "source": "user",
  "data": { "...": "event-specific" }
}
```

## Transport (SNS + SQS)

- Each producer publishes to its own SNS topic, named `<service>-events` (e.g. `user-events`).
- Each consumer has its own SQS queue subscribed to the topics it needs. A subscription filter
  policy on the `eventType` message attribute can limit which events reach the queue.
- Queues, topics and filter policies are defined in `infra/terraform` (`event_types` per queue in
  `environments/*.tfvars`). A consumer that starts handling a new event type must add it there,
  otherwise SNS drops the event before it reaches the queue, without a log line.
  Example: `notification-queue` is subscribed to `user-events` (filter: `UserUpdated`,
  `GuardianLinked`, `GuardianUnlinked`) and to `exam-analysis-events` (no filter), with
  `notification-queue-dlq` after 5 receives; services only receive and delete messages.
- SNS message attributes:
  - `eventType`: same value as in the envelope
  - `traceparent`: W3C trace context of the request that caused the event (may be missing).
    Consumers continue this trace, so one request can be followed across services in Grafana.
- Producers write events to a transactional outbox in the same database transaction as the
  change, then publish them. An event is published only if the change was committed.

## Rules for consumers

- **At-least-once delivery.** The same event can arrive more than once. Store processed `eventId`s
  (`ProcessedEvent` / IslenmisOlay table) and skip duplicates.
- **No global ordering.** SNS standard topics don't guarantee order:
  - `UserUpdated` carries `recordVersion`. Ignore an event older than the stored version.
  - Membership events are keyed by `(classroomId, studentId)`, so their order doesn't matter for
    a local copy.
- **Personal data.** Payloads contain names and contact details (KVKK). Never log payloads. Log
  `eventId` and `eventType` only.

## Naming and versioning

- Event names are English PascalCase verbs in the past tense: `StudentAddedToClassroom`.
- Adding an optional field keeps `schemaVersion`. Consumers must ignore unknown fields.
- Removing or renaming a field, or changing its meaning, increases `schemaVersion`. The producer
  publishes the old version until all consumers have moved.

## Events

| Event | Producer | Consumers | When |
|---|---|---|---|
| `StudentAddedToClassroom` | user | exam-analysis, task, attendance | A student joins a classroom |
| `StudentRemovedFromClassroom` | user | exam-analysis, task, attendance | A student leaves a classroom (moved, removed, deactivated) |
| `UserUpdated` | user | notification, exam-analysis | A user is created or changed (full current state) |
| `GuardianLinked` | user | notification, attendance, task | A guardian is linked to a student, or the relationship changes |
| `GuardianUnlinked` | user | notification, attendance, task | A guardian link is removed or replaced |
| `ExamResultsPublished` | exam-analysis | notification | An admin published the results of a mock exam (once per exam). Optional `mockExamName`, `examDate` for the texts |
| `StudentResultAdded` | exam-analysis | notification | A result was added to an already published exam (late matching). Optional `mockExamName`, `examDate` |
| `StudentAbsent` | attendance | notification | A student ended up absent without an approved excuse (when attendance is taken or corrected) |
| `StudentExcusedByTeacher` | attendance | notification | The teacher excused a student without an approved guardian excuse (when attendance is taken or corrected) |
| `TaskAssigned` | task | notification | An assignment was handed to a student |
| `TaskDueSoon` | task | notification | An open assignment entered its reminder window |
| `TaskOverdue` | task | notification | The due time passed without a submission |
| `SubmissionGraded` | task | notification | A submission was evaluated (or evaluated again) |
