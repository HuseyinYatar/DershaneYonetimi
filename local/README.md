# Yerel geliştirme ortamı

AWS servisleri Floci ile, kimlik Keycloak ile yerelde çalışır. Gereken: Docker ve JDK 25.

## Başlatma

```bash
# Bağımlılıklar: PostgreSQL, Keycloak, Floci (SNS/SQS), Mailpit, Redis
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
| Gateway metrik, sağlık, build bilgisi | http://localhost:8001/metrics, `/health/readiness`, `/info` | — |
| Swagger UI (user) | http://localhost:8080/swagger-ui.html | "Authorize" → Keycloak |
| Metrikler, sağlık, build bilgisi | http://localhost:8081/metrics, `/health/readiness`, `/info` | — |
| Keycloak yönetim konsolu | http://localhost:8180 | `admin` / `admin` |
| İlk super-admin (uygulama kullanıcısı) | Swagger'dan giriş | `superadmin@dershane.local` / `admin` |
| Mailpit (şifre belirleme e-postaları) | http://localhost:8025 | — |
| Floci (AWS uç noktası) | http://localhost:4566 | herhangi bir anahtar |

Servislere yerelde doğrudan (user için `:8080`) de erişilebilir; servis token'ı kendisi doğruladığı için
gateway'i atlayan istek de geçerli bir JWT ister. Gateway'in hız sınırı kullanıcı başına saniyede 20
istek (en çok 40'lık ani artış); `RATE_LIMIT_REPLENISH_RATE` ve `RATE_LIMIT_BURST` ile değişir.

Bu şifreler yalnızca yerel ortam içindir. Gerçek gizli bilgiler Secrets Manager'dadır.

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
