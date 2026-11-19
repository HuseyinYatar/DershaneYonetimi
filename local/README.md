# Yerel geliştirme ortamı

AWS servisleri Floci ile, kimlik Keycloak ile yerelde çalışır. Gereken: Docker; user, attendance, task ve messaging için JDK 25,
exam-analysis için Python 3.14+ ve [uv](https://docs.astral.sh/uv/).

## Başlatma

Tek komutla tüm backend (bağımlılıklar, servisler, gateway), servisler hazır olana kadar bekler:

```bash
local/up.sh            # çalışmayanları başlat (imaj yoksa derler)
local/up.sh --build    # kod değiştiyse imajları yeniden derle
local/up.sh --seed     # + mock veri (aşağıda "Mock veri")
local/up.sh --reset    # önce volume'ları sil (veritabanı dahil)
local/up.sh down       # durdur (container ve veri kalır)
```

Floci kaynakları `floci-data` volume'una yazar (`FLOCI_STORAGE_MODE=persistent`), bu yüzden yeniden
başlatmada `floci-init` ~10 sn'de "No changes" ile biter. Yalnızca `--reset` (`down -v`) sonrası 42 kaynağı
baştan açar (kuyruk başına ~25 sn, toplam 2–3 dk); takılmış gibi görünür ama ilerler.

Elle:

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
| Frontend (`cd frontend && npm run dev`) | http://localhost:3000 | Keycloak'a yönlendirir |
| notification (API / Swagger UI) | http://localhost:8003, http://localhost:8003/docs | "Authorize" → Keycloak |
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
| Giriş sayfası teması | `local/keycloak/themes/dershane` (compose'ta bağlı, önbellek kapalı) | CSS/şablon düzenle, sayfayı yenile |
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

## Mock veri

Öğrenci, öğretmen ve yönetici panellerini gerçek içerikle görmek için yığını örnek veriyle doldurur. Varsayılan
olarak yalnızca yerel yığına dokunur. Aynı betik kümede `SEED_TARGET=prod` ile `demo-seed` Job'unun içinde de
çalışır (`k8s/README.md`, "Demo verisi"). Gereken: Node 22+ (bağımlılık yok) ve `--profile services` ile çalışan yığın.

```bash
node local/seed/seed.mjs
```

Betik tekrar çalıştırılabilir: her adım önce var olanı arar (işaret: "Demo 12-A" ve "Demo 12-B" sınıfları, "Demo TYT
Deneme N" sınavları, görev başlıkları). Veriyi sıfırlamak için `docker compose ... down -v` yap, yığını yeniden kaldır
ve betiği çalıştır. Veri gerçek API'lerle (gateway `:8000`) açıldığı için olaylar akar ve diğer servislerin
kopyaları (`StudentReplica`, `MembershipReplica`, iletişim kopyaları) dolar; betik kopyalar dolana kadar bekler.

**Yerel hesaplar** (şifreler Keycloak admin API'siyle bilinen değere ayarlanır; kümede hepsinin ortak şifresi
Secrets Manager'daki `dershane/demo/password`'tür):

| Rol | Ad | Kullanıcı adı | Şifre | AYT alanı | Not |
|---|---|---|---|---|---|
| Öğrenci | Ayşe Yılmaz | `mrkz-1001` | `Demo1234!` | SAY | Ana öğrenci (12-A): velisi, yoklama geçmişi, teslim ve notlanan ödevleri var |
| Öğrenci | Mehmet Kaya | `mrkz-1002` | `Demo1234!` | SAY | 12-A'nın en iyisi |
| Öğrenci | Elif Demir … Deniz Aydın | `mrkz-1003` … `mrkz-1009` | `Demo1234!` | SAY/EA | 12-A |
| Öğrenci | Merve Koç | `mrkz-1010` | `Gecici123!` | SAY | Yalnızca yerelde geçici şifre: ilk girişte "şifre belirle" sayfası |
| Öğrenci | Ahmet Polat … Yağmur Bulut | `mrkz-1011` … `mrkz-1020` | `Demo1234!` | SAY/EA | 12-B (İrem Aslan `mrkz-1014` en iyisi) |
| Öğretmen | Kemal Aksoy | `ogretmen@dershane.local` | `Demo1234!` | | Demo 12-A'nın Matematik ve Türkçe öğretmeni |
| Öğretmen | Seda Kılıç | `ogretmen2@dershane.local` | `Demo1234!` | | Demo 12-B'nin Matematik ve Türkçe öğretmeni |
| Veli | Fatma Yılmaz | `05551234567` | `Demo1234!` | | Ayşe'nin velisi (anne) |
| Super-admin | | `superadmin@dershane.local` | `admin` | | Bütün şubeler, denemeler ve kullanıcılar |

AYT alanı: SAY öğrencileri AYT'de Matematik ve Fen'i, EA öğrencileri Matematik ve Edebiyat–Sosyal Bilimler-1'i
yanıtlar (80 sorunun 60–74'ü doğru, 2–5'i boş); diğer iki teste girmezler. Girilmeyen test boş sayılmaz ve sonuç
oluşturmaz; SAY öğrencisinin SÖZ puanı "0,5 net şartı" nedeniyle hesaplanmaz.

Şube olarak var olan `MRKZ` kullanılır (yoksa "Demo Şube" / `DMO` açılır); kullanıcı adı `{şubekodu}-{öğrencino}`
kuralıyla `mrkz-1001` olur.

Ne oluşur:
- **API ile:** ders kataloğu (Matematik, Türkçe), `Demo 12-A` ve `Demo 12-B` (12. sınıf), iki öğretmen ve
  atamaları, 20 öğrenci (üyelik 2026-09-01'den), veli bağı; exam-analysis: şube zayıf konu ayarı (son 3 deneme,
  eşik 0,5), 5 TYT denemesi (iki sınıfın öğrencileri birlikte; kurum içi puanlama, cevap anahtarı, optik dosya
  yükleme, açıklama; Ayşe'nin neti denemeden denemeye artar, bazı konularda zayıf kalır; açıklama bildirimlerini
  üretir), 2 AYT denemesi (19 ve 5 gün önce, `ayt-fwf-v1`; SAY/EA/SÖZ puanları TYT Deneme 3 ve 4'ün netleriyle
  birleşir); attendance: 12-A pazartesi/çarşamba/cuma, 12-B salı/perşembe haftalık program, son 4 haftanın
  oturumları (yönetici geçmiş tarihli oturum açıp gerekçeyle yoklama alır; Ayşe: 8 var, 2 geç, 1 yok, 1 izinli) ve
  yerelde `generate-sessions` işiyle gelecek 7 günün oturumları; task: 12-A'ya 7, 12-B'ye 2 görev, Ayşe 3 teslim
  yapar, öğretmeni 2'sini notlar.
- **Doğrudan işlemle (API'nin yapamadıkları, yalnızca yerelde):**
  - Şifreler Keycloak admin API'siyle (master `admin`/`admin`; kümede `dershane/keycloak/admin`) ayarlanır.
  - Katalog (`seed-catalog`) exam-analysis container'ında çalıştırılır (kümede exam-analysis'in migration Job'u yapar).
  - Kopyaların dolduğu SQL ile kontrol edilir (kümede exam-analysis API'sine sorulur).
  - `OVERDUE` (Gecikti) durumu zamanla oluştuğu için "Limit çalışma kağıdı" ve "Fizik: Vektörler tekrar testi" atamalarının
    `due_at` değeri `task_service` veritabanında SQL ile 2 gün geriye alınır, ardından task servisinin gerçek
    `check-deadlines` işi çalıştırılır ve atamaları `OVERDUE` yapar (olay yolu da çalışır). Kümede bu adım yoktur;
    süre dolunca CronJob işaretler.

```bash
docker compose -f local/docker-compose.yml down -v   # veritabanı dahil her şeyi siler
```

Realm tanımı (`keycloak/dershane-realm.json`) entegrasyon testlerinde de kullanılır. Değiştirdiğinde
`services/user` içinde `./mvnw verify` ile Keycloak testlerini çalıştır.
