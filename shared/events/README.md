# Event schemas

JSON Schema (draft 2020-12) contracts of the events exchanged over the message broker
(SNS topic `dershane-events`, one SQS queue per consuming service, filtered on the
`eventType` message attribute). Every event uses the envelope in `_envelope.v1.json`.

| Event | Producer | Consumers |
|---|---|---|
| `ExamResultsPublished` | exam-analysis | notification |
| `StudentResultAdded` | exam-analysis | notification |
| `StudentAddedToClass` | user | exam-analysis, task, attendance |

Rules:
- Event names are English PascalCase; payload fields are camelCase.
- A breaking change gets a new file (`.v2.json`) and a new `version`; producers keep sending
  the old version until every consumer has moved.
- Payloads carry identifiers, not personal data, unless a consumer needs a local copy
  (`StudentAddedToClass`). Such payloads are never logged.
- Each service has a contract test that validates the events it produces against these files.
