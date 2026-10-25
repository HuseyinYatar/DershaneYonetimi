# Local environment

Development dependencies, with Floci in place of AWS. Nothing here is used in production.

```bash
docker compose -f local/docker-compose.yml up -d              # postgres, pgadmin, redis, floci, keycloak
docker compose -f local/docker-compose.yml --profile app up -d # + exam-analysis api, worker, migrations
```

| Service | Address | Notes |
|---|---|---|
| PostgreSQL | localhost:5433 | one database per service, created by `postgres/init.sql` |
| pgAdmin | http://localhost:5050 | server "dershane (local)" is pre-registered; password `exam_analysis` |
| Redis | localhost:6379 | |
| Floci | http://localhost:4566 | `AWS_ENDPOINT_URL`, any non-empty credentials |
| Keycloak | http://localhost:8080 | admin console: admin / admin, realm `dershane` |

Keycloak users (password = username): `admin1` (ADMIN), `teacher1` (TEACHER), `student1..3` (STUDENT).
Staff users carry the `branch_id` claim of branch `11111111-1111-4111-8111-111111111111`.
`dev-students.json` loads the same students into exam-analysis (`python -m app.cli seed-students`).

Get a token:

```bash
curl -s -d grant_type=password -d client_id=dershane-web -d username=admin1 -d password=admin1 \
  http://localhost:8080/realms/dershane/protocol/openid-connect/token | jq -r .access_token
```
