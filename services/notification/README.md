# notification

Turns events of the other services into e-mail, SMS and in-app notifications. FastAPI +
SQLAlchemy 2 (sync, psycopg 3), PostgreSQL, events from SQS, e-mail over SMTP.

The service never asks another service for data: names, contact details and guardian links are
local copies fed by the user service's events; the recipients of an exam notification come in the
event itself (`studentIds`).

## Run locally

```bash
docker compose -f ../../local/docker-compose.yml up -d     # postgres, floci (+ init), keycloak, mailpit
cp .env.example .env
uv sync
uv run alembic upgrade head
uv run uvicorn app.main:create_app --factory --reload      # API on :8000, docs on /docs
uv run python -m app.worker                                # consumer + delivery
```

Or in containers: `docker compose -f ../../local/docker-compose.yml --profile services up -d`
(API on :8002, worker metrics on :9102). Mails land in Mailpit: http://localhost:8025.

## Tests

```bash
uv run ruff check . && uv run ruff format --check .
uv run pytest --cov=app                                    # Testcontainers PostgreSQL (needs Docker)
TEST_DATABASE_URL=postgresql+psycopg://... uv run pytest   # or any empty PostgreSQL database
```

SQS is mocked with moto, SMTP with a fake; nothing reaches AWS or a mail server.

## Processes

| Process | Command | Does |
|---|---|---|
| API | `uvicorn app.main:create_app --factory` | the caller's in-app notifications and preferences |
| Worker | `python -m app.worker` | A: consume the queue, B: deliver pending e-mail/SMS |
| Migrations | `alembic upgrade head` | once per release (Job / init container) |

Worker liveness: `python -m app.worker.healthcheck` (heartbeat file).

## Events in, notifications out

```
user-events ──(filter: UserUpdated, GuardianLinked, GuardianUnlinked)──┐
                                                                        ├─> notification-queue ─> worker
exam-analysis-events ──(no filter)──────────────────────────────────────┘        └─ DLQ after 5 receives
```

| Event | Effect |
|---|---|
| `UserUpdated` | upsert `contact_replica` (ignored if `recordVersion` is older than the stored one) |
| `GuardianLinked` | upsert `guardian_link_replica` (one guardian per student) |
| `GuardianUnlinked` | delete the link only if the guardian matches |
| `ExamResultsPublished` | notifications for every student and their guardian |
| `StudentResultAdded` | notifications for one late-matched student and their guardian |

Per message, in one transaction: validate → `processed_event` (duplicates skipped) → handler →
commit → delete the message. Invalid or failing messages are not deleted; SQS moves them to
`notification-queue-dlq` after 5 receives.

Channels per recipient: `IN_APP` always (written as `SENT`); `EMAIL` / `SMS` only with an address
and unless turned off in `notification_preference` (written as `PENDING`). Inactive or unknown
recipients get nothing; every skip is counted in `notifications_skipped_total{reason}` and in the
`notification.planned` log line.

Delivery: rows are claimed with `FOR UPDATE SKIP LOCKED`. A temporary error (server down, 4xx)
retries after 1, 5, 15, 60 minutes; a permanent error (5xx, recipient refused) or the 5th attempt
marks the row `FAILED`. SMS has no provider yet: `LoggingSmsSender` sends nothing and logs it.

Texts: Jinja2 files under `src/app/templates/` (`<set>/<audience>.<title|body|sms>.txt`). No scores
or nets in the texts, only a link.

## API (`/api/v1`)

Any logged-in user; everyone sees and changes only their own data (`recipient_id = sub`).

| Endpoint | |
|---|---|
| `GET /notifications?unreadOnly=&limit=&offset=` | in-app notifications, newest first |
| `GET /notifications/unread-count` | polled by the frontend |
| `POST /notifications/{id}/read` | 204; someone else's id answers 404 |
| `POST /notifications/read-all` | `{"updated": n}` |
| `GET /notification-preferences` | e-mail/SMS on/off per notification kind |
| `PUT /notification-preferences` | `IN_APP` cannot be turned off (422 `CHANNEL_NOT_CONFIGURABLE`) |

Errors are RFC 9457 ProblemDetail (`application/problem+json`) with an English `errorCode` and a
Turkish `detail` (`Accept-Language: en` for English).

## Glossary (UML `bildirim` page → code)

| UML | Code |
|---|---|
| Bildirim | `notification` |
| BildirimTercihi | `notification_preference` (row = channel turned off) |
| IslenmisOlay | `processed_event` |
| IletisimKopya | `contact_replica` |
| VeliOgrenciKopya | `guardian_link_replica` |
| Kanal: EPOSTA / SMS / UYGULAMA | `Channel`: `EMAIL` / `SMS` / `IN_APP` |
| GonderimDurumu: BEKLIYOR / GONDERILDI / HATA | `DeliveryStatus`: `PENDING` / `SENT` / `FAILED` |
| BildirimSablonu | Jinja2 files under `src/app/templates/` (no table) |
