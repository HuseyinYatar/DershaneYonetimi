# Yerel geliştirme ortamı

AWS servisleri Floci ile, kimlik Keycloak ile yerelde çalışır. Gereken: Docker; user, attendance, task ve messaging için JDK 25,
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
| task (API / Swagger UI) | http://localhost:8084, http://localhost:8084/swagger-ui.html | "Authorize" → Keycloak |
| task metrik, sağlık, build bilgisi | http://localhost:8085/metrics, `/health/readiness`, `/info` | — |
| messaging (API / Swagger UI) | http://localhost:8086, http://localhost:8086/swagger-ui.html | "Authorize" → Keycloak |
| messaging metrik, sağlık, build bilgisi | http://localhost:8087/metrics, `/health/readiness`, `/info` | — |
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
uv run uvicorn app.main:create_app --factory --reload                  # API :8000
uv run python -m app.worker                                            # optik dosya + outbox + user olayları
```

`floci-init` (`infra/terraform`) exam-analysis için `exam-analysis-optical-files` bucket'ını,
`exam-analysis-events` topic'ini (inceleme kuyruğu `local-inspect-exam-analysis-events`) ve
`user-events`'e abone `exam-analysis-user-events` kuyruğunu (filtreli, DLQ: `exam-analysis-user-events-dlq`)
açar.

## attendance

```bash
cd services/attendance
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # API :8080 (container: 8082)
```

Yerel profil `db/local/R__local_seed.sql` ile `local/dev-students.json`'daki üç öğrencinin sınıf üyeliğini
ekler (sınıf `22222222-2222-4222-8222-222222222222`). Üyelik ve veli bağı kopyaları user servisinin
olaylarından (`attendance-user-events` kuyruğu) dolar; elle doldurma endpoint'i yoktur. Yerelde yeni
öğrenci, sınıf ve veli user servisinden (`:8080`) açılır. Oturumlar haftalık programdan CronJob ile üretilir; yerelde aynı imaj tek seferlik çalıştırılır:

```bash
docker compose -f local/docker-compose.yml run --rm attendance --attendance.job=generate-sessions
docker compose -f local/docker-compose.yml run --rm attendance --attendance.job=expire-sessions
```

`floci-init` attendance için `attendance-events` topic'ini (inceleme kuyruğu
`local-inspect-attendance-events`) ve `user-events`'e abone `attendance-user-events` kuyruğunu açar.
Kuyruk yalnızca `infra/terraform/environments/local.tfvars`'ta listelenen olay tiplerini alır.

## task

```bash
cd services/task
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # API :8080 (container: 8084)
```

Yerel profil `db/local/R__local_seed.sql` ile `local/dev-students.json`'daki üç öğrencinin sınıf üyeliğini
ekler (sınıf `22222222-2222-4222-8222-222222222222`); üyelik ve veli bağı kopyaları normalde
`task-user-events` kuyruğundan dolar; elle doldurma endpoint'i yoktur. Yerelde yeni öğrenci, sınıf ve
veli user servisinden (`:8080`) açılır. Tekrarlı görevlerin günlük atamaları ve gecikme/hatırlatma işlemi CronJob ile yapılır; yerelde aynı imaj tek
seferlik çalıştırılır:

```bash
docker compose -f local/docker-compose.yml run --rm task --task.job=generate-assignments   # günde bir (00:05)
docker compose -f local/docker-compose.yml run --rm task --task.job=check-deadlines        # her 15 dk
```

`floci-init` task için `task-events` topic'ini (inceleme kuyruğu `local-inspect-task-events`) ve
`user-events`'e abone `task-user-events` kuyruğunu açar. `local/postgres/init.sql` yalnızca boş
volume'da çalışır: var olan bir ortamda `docker compose down -v` yap veya `task_service` kullanıcı ve
veritabanını elle oluştur (`CREATE USER task_service ...; CREATE DATABASE task_service OWNER task_service;`).

## messaging

```bash
cd services/messaging
./mvnw spring-boot:run -Dspring-boot.run.profiles=local   # API :8080 (container: 8086), Redis gerekir
```

Öğrenci ve öğretmen kopyası `messaging-user-events` kuyruğundan (`UserUpdated`) dolar; yerelde kullanıcıları
user servisinden (`:8080`) aynı şubede aç. Akış: öğrenci token'ıyla `POST /api/v1/conversations`, ardından
`POST /api/v1/chat/tickets` ile bilet al ve `ws://localhost:8000/api/v1/chat/ws?ticket=…` adresine bağlan
(örn. `websocat`); karşı taraf mesaj gönderince `MESSAGE` çerçevesi gelir. Soket kapalıyken (veya
`presence:{id}` anahtarı yokken) gelen her mesaj `messaging-events` topic'ine bir `NewMessage` yazar
(inceleme kuyruğu `local-inspect-messaging-events`). `local/postgres/init.sql` yalnızca boş volume'da
çalışır: var olan bir ortamda `docker compose down -v` yap veya `messaging_service` kullanıcı ve veritabanını
elle oluştur.

## Port çakışması

Bilgisayarında başka bir PostgreSQL 5432'yi kullanıyorsa `local/.env` (git'e girmez) oluştur:

```bash
echo "POSTGRES_PORT=5433" > local/.env
```

Servislerin `.env` dosyasındaki veritabanı adresini de aynı porta çevir.

## Yayınlanan olayları görmek

`floci-init`, her topic için bir `local-inspect-<topic>` kuyruğu açar (ör. `local-inspect-user-events`):

```bash
docker compose -f local/docker-compose.yml run --rm aws-cli \
  sqs receive-message --max-number-of-messages 10 --message-attribute-names All \
  --queue-url http://floci:4566/000000000000/local-inspect-user-events
```

## Altyapıyı (Terraform) değiştirmek

Topic, kuyruk ve bucket'lar `infra/terraform` ile yönetilir. `tfvars` değiştirdikten sonra:

```bash
docker compose -f local/docker-compose.yml run --rm floci-init
```

Aynı kod gerçek AWS'ye de uygulanır (`aws_endpoint_url` ve `emulator` verilmez). Terraform'u
bilgisayarından çalıştırırsan Floci'nin `localhost` adresini ver. State bilgisayarında kalır ve
container'ınkinden ayrıdır; ikisini karıştırma (kuyruk URL'leri state'te adres içerir):

```bash
cd infra/terraform
AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test terraform apply \
  -var-file=environments/local.tfvars -var=aws_endpoint_url=http://localhost:4566
```

`down -v` Floci'yi ve container state'ini birlikte sıfırlar. Yalnızca Floci yeniden başlarsa
(`restart floci`) `floci-init`'i tekrar çalıştırman yeter; Terraform eksik kaynakları yeniden açar.

## Sıfırlama

```bash
docker compose -f local/docker-compose.yml down -v   # veritabanı dahil her şeyi siler
```

Realm tanımı (`keycloak/dershane-realm.json`) entegrasyon testlerinde de kullanılır. Değiştirdiğinde
`services/user` içinde `./mvnw verify` ile Keycloak testlerini çalıştır.
