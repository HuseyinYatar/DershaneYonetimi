# Yerel geliştirme ortamı

AWS servisleri Floci ile, kimlik Keycloak ile yerelde çalışır. Gereken: Docker; user ve attendance için JDK 25,
exam-analysis için Python 3.14+ ve [uv](https://docs.astral.sh/uv/).

## Başlatma

```bash
# Bağımlılıklar: PostgreSQL, Keycloak, Floci (SNS/SQS/S3), Mailpit, Redis
docker compose -f local/docker-compose.yml up -d

# Servisi IDE'den ya da Maven ile çalıştır (local profili localhost portlarını kullanır)
cd services/user
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# ...ya da servisi de container olarak çalıştır
docker compose -f local/docker-compose.yml --profile services up -d --build
```

## Adresler

| Ne | Adres | Giriş |
|---|---|---|
| API gateway (tek giriş noktası) | http://localhost:8000/api/v1/... | `Authorization: Bearer <token>` |
| Gateway metrik, sağlık, build bilgisi | http://localhost:8002/metrics, `/health/readiness`, `/info` | — |
| Swagger UI (user) | http://localhost:8080/swagger-ui.html | "Authorize" → Keycloak |
| Swagger UI (exam-analysis) | http://localhost:8000/docs (container: 8001) | "Authorize" → keycloak |
| pgAdmin (`--profile tools`) | http://localhost:5050 | sunucu şifresi = veritabanı kullanıcı adı |
| Metrikler, sağlık, build bilgisi (user) | http://localhost:8081/metrics, `/health/readiness`, `/info` | — |
| attendance (API / Swagger UI) | http://localhost:8082, http://localhost:8082/swagger-ui.html | "Authorize" → Keycloak |
| attendance metrik, sağlık, build bilgisi | http://localhost:8083/metrics, `/health/readiness`, `/info` | — |
| Keycloak yönetim konsolu | http://localhost:8180 | `admin` / `admin` |
| İlk super-admin (uygulama kullanıcısı) | Swagger'dan giriş | `superadmin@dershane.local` / `admin` |
| Mailpit (şifre belirleme e-postaları) | http://localhost:8025 | — |
| Floci (AWS uç noktası) | http://localhost:4566 | herhangi bir anahtar |

Servislere yerelde doğrudan (user için `:8080`) de erişilebilir; servis token'ı kendisi doğruladığı için
gateway'i atlayan istek de geçerli bir JWT ister. Gateway'in hız sınırı kullanıcı başına saniyede 20
istek (en çok 40'lık ani artış); `RATE_LIMIT_REPLENISH_RATE` ve `RATE_LIMIT_BURST` ile değişir.

Bu şifreler yalnızca yerel ortam içindir. Gerçek gizli bilgiler Secrets Manager'dadır.

## exam-analysis

```bash
cd services/exam-analysis
cp .env.example .env
uv sync
uv run alembic upgrade head
uv run python -m app.cli seed-catalog
uv run python -m app.cli seed-students ../../local/dev-students.json   # user olayları gelene kadar
uv run uvicorn app.main:create_app --factory --reload                  # API :8000
uv run python -m app.worker                                            # optik dosya işleyici + outbox
```

`floci-init` exam-analysis için `exam-analysis-optical-files` bucket'ını, `exam-analysis-events`
topic'ini (inceleme kuyruğu `local-inspect-exam-analysis-events`) ve `user-events`'e abone
`exam-analysis-user-events` kuyruğunu açar.

## attendance

```bash
cd services/attendance
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # API :8080 (container: 8082)
```

Yerel profil `db/local/R__local_seed.sql` ile `local/dev-students.json`'daki üç öğrencinin sınıf üyeliğini
ekler (sınıf `22222222-2222-4222-8222-222222222222`). Servisler arası iletişim (4. hafta) gelene kadar
üyelik ve veli bağı kopyalarını yönetici `PUT /api/v1/attendance-replicas/memberships/{id}` ve
`/guardian-links/{studentId}` ile doldurur. Oturumlar haftalık programdan CronJob ile üretilir; yerelde
aynı imaj tek seferlik çalıştırılır:

```bash
docker compose -f local/docker-compose.yml run --rm attendance --attendance.job=generate-sessions
docker compose -f local/docker-compose.yml run --rm attendance --attendance.job=expire-sessions
```

`floci-init` attendance için `attendance-events` topic'ini (inceleme kuyruğu
`local-inspect-attendance-events`) ve `user-events`'e abone `attendance-user-events` kuyruğunu açar.

## Port çakışması

Bilgisayarında başka bir PostgreSQL 5432'yi kullanıyorsa `local/.env` (git'e girmez) oluştur:

```bash
echo "POSTGRES_PORT=5433" > local/.env
```

Servislerin `.env` dosyasındaki veritabanı adresini de aynı porta çevir.

## Yayınlanan olayları görmek

`floci-init`, `user-events` topic'ini ve ona bağlı `local-inspect-user-events` kuyruğunu açar:

```bash
docker compose -f local/docker-compose.yml run --rm --entrypoint aws floci-init \
  sqs receive-message --max-number-of-messages 10 --message-attribute-names All \
  --queue-url http://floci:4566/000000000000/local-inspect-user-events
```

## Sıfırlama

```bash
docker compose -f local/docker-compose.yml down -v   # veritabanı dahil her şeyi siler
```

Realm tanımı (`keycloak/dershane-realm.json`) entegrasyon testlerinde de kullanılır. Değiştirdiğinde
`services/user` içinde `./mvnw verify` ile Keycloak testlerini çalıştır.
