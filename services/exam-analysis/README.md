# exam-analysis

Mock exam (deneme) scoring and analysis: optical file import, nets, estimated scores, topic
analysis, weak topics and progress reports. FastAPI + SQLAlchemy 2 (sync, psycopg 3) +
pandas, PostgreSQL, optional Redis cache, events through SNS/SQS.

The data model follows the `deneme-analizi` page of `dershane-entity-uml-fk.drawio.html`;
the glossary at the end maps the Turkish UML names to the English code names.

## Run locally

```bash
docker compose -f ../../local/docker-compose.yml up -d     # postgres, redis, floci (+ init), keycloak
cp .env.example .env
uv sync
uv run alembic upgrade head
uv run python -m app.cli seed-catalog                      # TYT / AYT / YDT / LGS catalog, optical formats
uv run python -m app.cli seed-students ../../local/dev-students.json
uv run uvicorn app.main:create_app --factory --reload      # API on :8000, docs on /docs
uv run python -m app.worker                                # import worker + outbox relay
```

`floci-init` (`local/floci/init.sh`) creates the bucket, the `exam-analysis-events` topic and the
`exam-analysis-user-events` queue, so `.env.example` can name the topic ARN directly. The service
can also run in containers: `docker compose -f ../../local/docker-compose.yml --profile services up -d`
(API on :8001).

Swagger UI (`/docs`) logs in through Keycloak: **Authorize → keycloak → Authorize** (client
`dershane-swagger`, PKCE; the password grant is disabled). The first user is
`superadmin@dershane.local` / `admin`; further users are created by the user service.

## Tests

```bash
uv run ruff check . && uv run ruff format --check .
uv run pytest --cov=app                                    # Testcontainers PostgreSQL (needs Docker)
TEST_DATABASE_URL=postgresql+psycopg://... uv run pytest   # or any empty PostgreSQL database
```

`tests/unit` needs nothing; `tests/api` and `tests/integration` need PostgreSQL and are skipped
without it. AWS calls are tested with moto; nothing reaches real AWS.

## Processes

| Process | Command | Scales on |
|---|---|---|
| API | `uvicorn app.main:create_app --factory` | requests |
| Worker | `python -m app.worker` | queued import jobs (CPU-heavy pandas work; HPA target) |
| Migrations | `alembic upgrade head` | run once per release (Job / init container) |

All three use the same image. The worker takes jobs from the `import_job` table with
`SELECT … FOR UPDATE SKIP LOCKED`, so replicas never process the same file; a job whose
worker died is requeued after `WORKER_STALE_JOB_TIMEOUT_SECONDS`. Liveness:
`python -m app.worker.healthcheck` (heartbeat file).

## Flow

1. Admin creates a mock exam (`DRAFT`), chooses the scoring source (`INSTITUTIONAL` / `NATIONAL`).
2. Admin uploads the answer key → `KEY_READY`. Every booklet numbers its questions 1..N.
3. Admin uploads the optical file → `ImportJob` `QUEUED` → worker → `COMPLETED` / `FAILED` with `errorMessage`.
   Matched rows become results; the rest become `UnmatchedRow`s with a reason. The exam becomes `PROCESSED`.
4. Admin matches (`STUDENT_NOT_FOUND`, `DUPLICATE_STUDENT`) or ignores unmatched rows.
5. Admin enters national statistics when the publisher announces them (NATIONAL exams only get scores then).
6. Admin publishes → `PUBLISHED`, `ExamResultsPublished` event, weak topics recomputed.
   Later matches publish `StudentResultAdded` for that student only.

Cancelling a question re-grades the stored answers (no file needed); cancelled questions are
excluded from correct/wrong/blank and topic totals. A question appears once per booklet with
different numbers, so each booklet's item is cancelled separately.

## API (`/api/v1`)

| | Endpoint | Role |
|---|---|---|
| Catalog | `GET/POST /exam-types`, `/exam-types/{id}/subjects`, `/subjects/{id}/topics`, `/exam-types/{id}/score-types`; `PUT /score-types/{id}/weights`; `GET/POST /optical-formats` | read: any, write: admin |
| Exams | `POST/GET /mock-exams`, `GET /mock-exams/{id}`, `PUT/GET …/answer-key`, `POST …/answer-key/{itemId}/cancel`, `POST …/publish` | admin (reads: teacher) |
| Import | `POST /mock-exams/{id}/import-jobs` (multipart: `file`, `opticalFormatId`), `GET /import-jobs/{id}`, `GET /import-jobs/{id}/unmatched-rows`, `POST /unmatched-rows/{id}/match`, `…/ignore` | admin |
| Statistics | `GET …/statistics`, `PUT …/statistics/national` | teacher / admin only |
| Reports | `GET /mock-exams/{id}/results`, `…/topic-analysis` | teacher, admin |
| Students | `GET /students/{id}/results`, `…/results/{mockExamId}`, `…/progress?examTypeId=`, `…/weak-topics` | the student, teachers/admins of the branch |
| Settings | `GET/PUT /branches/{branchId}/weak-topic-setting` | admin |
| Temporary | `PUT /admin/student-replicas` | admin |
| Ops | `/health/live`, `/health/ready`, `/metrics` | — |

Errors are `{"code", "message", "details"}` with stable codes (`ANSWER_KEY_NOT_READY`,
`STUDENT_NOT_IN_EXAM_BRANCH`, …). Teachers and admins act only on the branch in their token's
`branch_id` claim; a `super-admin` acts on every branch. Students see published exams only.
Guardian access needs the guardian–student link of the user service and is not available yet.

## Authentication

Keycloak issues the tokens; this service only verifies them (`app/auth/keycloak.py`): RS256
signature with keys from Keycloak's JWKS endpoint (fetched once, cached), `iss`, `aud`, `exp`.
No request calls Keycloak. The audience is `dershane-api`, shared by all backend services. Roles come
from `realm_access.roles` (`student`, `teacher`, `guardian`, `admin`, `super-admin`).

## Observability

- **Logs:** JSON on stdout (structlog), one event name per line (`import_job.completed`,
  `unmatched_row.matched`, `request.rejected` …) with `request_id`, `import_job_id`,
  `mock_exam_id`, `trace_id`. No silent early returns: every rejected operation raises a domain
  error that is logged once with its code, and every skipped background step logs a reason and
  increments a metric. Personal data (names, student numbers, raw optical lines, answers) is
  never logged; a test enforces it.
- **Metrics:** `/metrics` (API) and `:9100/metrics` (worker): request durations by route template,
  `import_jobs_total`, `import_job_duration_seconds`, `import_job_queue_wait_seconds`,
  `import_rows_total{result,reason}`, `score_calculation_skipped_total`, `outbox_pending`, …
- **Traces:** OpenTelemetry (`OTEL_ENABLED=true`, standard `OTEL_EXPORTER_OTLP_*` variables);
  the `traceparent` is stored with each outbox event and sent as an SNS message attribute.

## Temporary adapters (until the other services exist)

| Today | Later (week 4) |
|---|---|
| `PUT /admin/student-replicas` fills `student_replica` | `StudentAddedToClassroom` / `UserUpdated` consumer on `exam-analysis-user-events` + `processed_event`, same `replica_service.upsert` |
| Multipart upload stored in this service's bucket | `file_id` from the file service, content via presigned URL (only `FileStorage` changes) |
| — | `AnswerSheetsRecognized` from the omr service creates an `ImportJob` with `source=OMR` |

The `omr-canonical-v1` optical format is the contract with the omr service: 12-char student
number, booklet, 36-char sheet image file id, 200 answer columns (`*` = multiple marks, wrong).

## Open points

- Score type weights and base scores in `seed/catalog.json` are placeholders (`weightsVerified: false`)
  until checked against the ÖSYM/MEB guides; the final scaling formula is isolated in
  `app/scoring/standard_score.py::_apply_base_score`.
- Changing weights does not rescore existing exams until they are recalculated (new import,
  late match, national statistics, question cancellation).

## Glossary (UML → code)

| UML (Turkish) | Code | Table |
|---|---|---|
| SinavTuru | `ExamType` | `exam_type` |
| Ders | `Subject` | `subject` |
| Konu (`ustKonuId`) | `Topic` (`parent_topic_id`) | `topic` |
| PuanTuru / PuanTuruAgirligi | `ScoreType` / `ScoreTypeWeight` | `score_type` / `score_type_weight` |
| Deneme (`puanlamaKaynagi`) | `MockExam` (`scoring_source`) | `mock_exam` |
| AnahtarSoru (`iptal`) | `AnswerKeyItem` (`is_cancelled`) | `answer_key_item` |
| OptikFormat (`alanTanimi`) | `OpticalFormat` (`field_definition`) | `optical_format` |
| IceAktarmaIsi | `ImportJob` | `import_job` |
| EslesmeyenSatir (`hataNedeni`, `eslestirenId`) | `UnmatchedRow` (`reason`, `resolved_by`) | `unmatched_row` |
| OgrenciKopya | `StudentReplica` | `student_replica` |
| OgrenciSonuc (`subeSirasi`) | `StudentResult` (`branch_rank`) | `student_result` |
| OgrenciPuani (`tahminiPuan`) | `StudentScore` (`estimated_score`) | `student_score` |
| OgrenciCevap | `StudentAnswer` | `student_answer` |
| DersSonuc / KonuSonuc | `SubjectResult` / `TopicResult` | `subject_result` / `topic_result` |
| DenemeDersIstatistigi | `ExamSubjectStatistic` | `exam_subject_statistic` |
| OgrenciKonuDurumu | `StudentTopicStatus` | `student_topic_status` |
| ZayifKonuAyari (`pencereDeneme`, `basariEsigi`) | `WeakTopicSetting` (`window_size`, `success_threshold`) | `weak_topic_setting` |
| — | `OutboxEvent` | `outbox_event` |
| DenemeDurumu: TASLAK, ANAHTAR_HAZIR, ISLENDI, ACIKLANDI | `ExamStatus`: DRAFT, KEY_READY, PROCESSED, PUBLISHED | |
| IsDurumu: KUYRUKTA, ISLENIYOR, TAMAM, HATA | `JobStatus`: QUEUED, PROCESSING, COMPLETED, FAILED | |
| CevapDurumu: DOGRU, YANLIS, BOS, IPTAL | `AnswerStatus`: CORRECT, WRONG, BLANK, CANCELLED | |
| PuanlamaKaynagi: KURUM_ICI, ULKE_GENELI | `ScoringSource`: INSTITUTIONAL, NATIONAL | |
| EslesmeDurumu: BEKLIYOR, ESLESTIRILDI, YOKSAYILDI | `MatchStatus`: PENDING, MATCHED, IGNORED | |
| DenemeSonucuAciklandi / OgrenciSonucuEklendi / OgrenciSinifaEklendi | `ExamResultsPublished` / `StudentResultAdded` / `StudentAddedToClassroom` (user) | events |
