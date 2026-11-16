# file

Stores homework attachments, homework submissions and the text files the optical reading
service produces. Contents are in S3 (Floci locally), metadata and who a file is shared with in
PostgreSQL. Python 3.14, FastAPI, SQLAlchemy 2, Alembic, Pillow.

## What is stored and who sees it

| `purpose` | Uploaded by | Sent with the upload | Readable by |
|---|---|---|---|
| `TASK_ATTACHMENT` | teacher | `studentIds` (one or more) | the teacher and the listed students |
| `SUBMISSION` | student | `teacherId` | the student and that teacher |
| `OMR_RESULT` | optical reading service (`internal-service` role) | — | `internal-service` (exam-analysis) and super-admin |

Anyone else gets `404 FILE_NOT_FOUND`, the same answer as for a file that does not exist. The
service does not know tasks or classrooms: for a classroom's homework the frontend lists the
classroom's students (`GET /api/v1/classrooms/{id}/students`) and sends them as `studentIds`.

## API

| Method and path | What it does |
|---|---|
| `POST /api/v1/files` | multipart `file`, `purpose`, and `studentIds` or `teacherId`; optional `Idempotency-Key` header. `201` with `{id, purpose, contentType, sizeBytes, fileName, createdAt}` |
| `GET /api/v1/files/{id}` | the same metadata |
| `GET /api/v1/files/{id}/download-url` | `{url, expiresAt}`: presigned S3 address, valid 5 minutes, for the browser |
| `GET /api/v1/files/{id}/content` | the bytes, for services (exam-analysis) |

Errors are RFC 9457 ProblemDetail with an English `errorCode` and a Turkish `detail`
(`Accept-Language: en` for English).

## Checks on upload

- **Type from the content.** The first bytes decide: JPEG, PNG, WebP or PDF for homework files,
  UTF-8 text without NUL bytes for `OMR_RESULT`. The browser's Content-Type and the file
  extension are ignored. Anything else is `415 UNSUPPORTED_FILE_TYPE`.
- **Size.** Photos up to `MAX_IMAGE_BYTES` (10 MB), PDF and text up to `MAX_DOCUMENT_BYTES`
  (20 MB): `413 FILE_TOO_LARGE`. A request body over the larger limit is cut off while it
  arrives (`BodySizeLimitMiddleware`), before FastAPI spools it to disk.
- **Photos are re-encoded** with Pillow: EXIF (with the GPS position of phone photos) is dropped,
  the EXIF orientation is applied first, a longer edge than 2560 px is scaled down, images over
  40 MP are rejected before decoding (`422 INVALID_IMAGE`). PDFs and text are stored as sent.
- **Idempotency-Key:** the same key with the same file, purpose and recipients returns the first
  file; a different upload under the same key is `422 IDEMPOTENCY_KEY_REUSED`. Keys belong to
  their uploader and do not expire.

## Service accounts

The optical reading service and exam-analysis call this service with their own Keycloak service
account (client credentials): clients `omr-service` and `exam-analysis-service`, realm role
`internal-service`, audience `dershane-api`. They call `http://file-api:8000` directly, not
through the gateway. Getting a token locally:

```bash
curl -s -d grant_type=client_credentials -d client_id=omr-service \
  -d client_secret=omr-service-local-secret \
  http://localhost:8180/realms/dershane/protocol/openid-connect/token | jq -r .access_token
```

## Run locally

```bash
cd services/file
cp .env.example .env
uv sync
uv run alembic upgrade head
uv run uvicorn app.main:create_app --factory --port 8004
```

In Docker: `local/up.sh` starts `file-migrate` and `file-api` (`:8004`). Swagger UI is at
http://localhost:8004/docs (local and dev only).

`S3_PUBLIC_ENDPOINT_URL` is the address download URLs are signed for. A presigned URL's signature
covers the host, so inside the container (Floci at `http://floci:4566`) the URL is still signed
for `http://localhost:4566`, which the browser reaches. On AWS both are empty and the URL names the
regional endpoint (`<bucket>.s3.eu-central-1.amazonaws.com`).

Floci does not check presigned signatures; a tampered URL works locally. Signature and expiry are
only enforced on AWS.

## Tests

```bash
uv run ruff check .
uv run pytest --cov=app
```

API tests need PostgreSQL: `TEST_DATABASE_URL` or Docker for Testcontainers. S3 is
`InMemoryFileStorage` in the API tests and moto in the storage tests.

## Observability

- Logs: JSON on stdout; identifiers, sizes and content types only. File names and contents are
  never logged. `file.uploaded`, `file.download_url_issued` and `file.content_read` record who
  received which file.
- Metrics on `/metrics`, all with `application="file"`: `file_uploads_total`,
  `file_upload_size_bytes`, `file_reads_total`, `domain_errors_total`,
  `http_request_duration_seconds`.
- Health: `/health/live`, `/health/ready` (database only; an S3 outage answers
  `503 FILE_STORAGE_UNAVAILABLE` but keeps the pod in traffic).

## Known limits

- A student who joins a classroom later does not see homework files uploaded before.
- The listed students and the teacher are not checked against the uploader's branch.
- Files never attached to a task stay in S3.
- A download URL works for whoever has it until it expires (5 minutes).
- No virus scan; PDFs are not cleaned.
