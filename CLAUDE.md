# CLAUDE.md

Bu dosya, Claude'un (ve ekibin) bu depoda çalışırken bilmesi gereken proje bağlamını, mimari kararları ve kuralları içerir. Değişen her karar burada güncellenmelidir.

## Proje

**Mikroservis Mimarisi ve DevOps ile Dershane Yönetim Sistemi Tasarımı**
Sakarya Üniversitesi, tasarım projesi. Danışman: Muhammed Fatih Adak. Ekip: Ümmügül Çağlar, Hüseyin Yatar.

Dershaneler için web tabanlı yönetim sistemi. Temel işlevler:
- **Deneme analizi:** Optik okuyucu dosyasından puanlama, ders/konu bazlı analiz, net gelişimi, zayıf konu tespiti
- **Anlık mesajlaşma:** Öğrenci ile öğretmen arasında WhatsApp benzeri sohbet, fotoğraflı soru
- **Görev/ödev:** Öğretmenin öğrenciye veya sınıfa tek seferlik, günlük veya haftalık görev ataması, teslim ve takip
- **Yoklama ve bildirim:** Devamsızlık takibi; öğrenciye ve veliye otomatik bildirim

Roller: öğrenci, öğretmen, veli, yönetici.

**Kapsam dışı:** Mobil uygulama, ödeme/muhasebe, canlı ders (video).

Projenin asıl amacı cloud-native ve modern DevOps uygulamalarını göstermek: Kubernetes, GitOps, altyapının kod olarak tanımlanması (IaC), gözlemlenebilirlik, otomatik ölçekleme. Mimari kararlar bu amaca hizmet etmeli ve raporda savunulabilir olmalı.

## Ortamlar

| | Üretim | Geliştirme ve CI |
|---|---|---|
| Kubernetes | EC2 üzerinde kubeadm ile kendi kurduğumuz küme | kind |
| AWS servisleri | Gerçek AWS | **Floci** (yerel AWS emülatörü, `http://localhost:4566`) |
| İmaj deposu | Amazon ECR | Yerel registry |
| Veritabanı | RDS (PostgreSQL) | Floci RDS veya yerel PostgreSQL |
| Önbellek | ElastiCache (Redis) | Floci ElastiCache veya yerel Redis |
| Dosya | S3 | Floci S3 |
| Gizli bilgiler | Secrets Manager | Floci Secrets Manager |
| Giriş trafiği | Network Load Balancer + NGINX Ingress + Route 53 | NGINX Ingress |

Kurallar:
- AWS uç noktası kodda sabit yazılmaz; ortam değişkeninden okunur (`AWS_ENDPOINT_URL`). Yerelde Floci'yi, üretimde boş bırakılarak gerçek AWS'yi gösterir.
- Aynı Terraform kodu hem Floci'ye hem AWS'ye uygulanabilmeli. Ortama özgü farklar değişken dosyalarında tutulur.
- Floci bir emülatördür; son doğrulama AWS üzerinde yapılır. EC2 sunucuları demo ve test dışında durdurulur.

## Kubernetes kümesi (EC2 + kubeadm)

Yönetilen EKS yerine kümeyi EC2 üzerinde kendimiz kuruyoruz. Gerekçe: EKS kontrol düzlemini AWS yönettiği için Kubernetes'in iç yapısı görülmüyor; projenin amacı tam Kubernetes deneyimi.

- **Topoloji:** 1 control plane + 2 worker node (EC2). Tek control plane bilinçli bir sınırlılıktır, raporda belirtilir.
- **Kurulum:** EC2, VPC, subnet ve security group'lar Terraform ile açılır. Kubernetes kurulumu (containerd, kubeadm init/join) cloud-init veya Ansible ile otomatik yapılır; elle sunucuya girip kurulum yapılmaz.
- **Ağ eklentisi (CNI):** Calico veya Cilium.
- **ECR erişimi:** Node'lara ECR okuma yetkili IAM instance profile verilir.
- **Kalıcı disk:** Küme içinde durumlu servis çalışacaksa AWS EBS CSI sürücüsü kurulur. Veritabanı RDS'te olduğu sürece gerekmez.
- **Dış trafik:** NGINX Ingress Controller, önünde Network Load Balancer.
- **Metrik:** HPA için metrics-server kurulur.
- **etcd yedeği:** Control plane'deki etcd düzenli olarak S3'e yedeklenir.
- Küme sertifikaları ve kubeconfig depoya konmaz; Secrets Manager'da tutulur.

## Mikroservisler

| Servis | Sorumluluk | Dil | Veri | Olaylar |
|---|---|---|---|---|
| api-gateway | Tek giriş noktası, yönlendirme, JWT doğrulama, hız sınırlama, CORS | Java (Spring Cloud Gateway, WebFlux); ileride AWS API Gateway olabilir | Redis (hız sınırı sayaçları) | — |
| identity | Giriş, token, roller | **Keycloak** | PostgreSQL | — |
| user | Öğrenci/öğretmen/veli/yönetici profilleri, sınıf, şube, ders kataloğu, veli–öğrenci eşleşmesi; Keycloak hesaplarını açar | Java (Spring Boot) | PostgreSQL | Yayınlar: `StudentAddedToClassroom`, `StudentRemovedFromClassroom`, `UserUpdated`, `GuardianLinked`, `GuardianUnlinked` |
| exam-analysis | Optik dosya içe aktarma, puanlama, konu analizi, gelişim raporları | Python (FastAPI + pandas) | PostgreSQL, Redis | Yayınlar: `ExamResultsPublished`, `StudentResultAdded`; Dinler: `StudentAddedToClassroom`, `UserUpdated` |
| messaging | WebSocket sohbet, fotoğraflı soru, okundu bilgisi | Java (Spring Boot) | PostgreSQL, Redis Pub/Sub | Yayınlar: `NewMessage` (alıcı çevrimdışıysa) |
| task | Görev/ödev atama, tekrarlı görevler, teslim ve değerlendirme | Java (Spring Boot) | PostgreSQL | Yayınlar: `TaskAssigned`, `TaskDueSoon`, `TaskOverdue`, `SubmissionGraded`; Dinler: `StudentAddedToClassroom`, `StudentRemovedFromClassroom` |
| attendance | Derse katılım, devamsızlık | Java (Spring Boot) | PostgreSQL | Yayınlar: `StudentAbsent`; Dinler: `StudentAddedToClassroom`, `StudentRemovedFromClassroom` |
| notification | Olayları dinleyip e-posta, SMS, uygulama içi bildirim gönderir | Python (FastAPI) | PostgreSQL | Dinler: yukarıdaki olaylar (iletişim bilgisi için `UserUpdated`, veli için `GuardianLinked`/`GuardianUnlinked`) |
| file | Fotoğraf, ödev, materyal yükleme/indirme | Java (Spring Boot) | S3 | — |

Frontend: Next.js (React, TypeScript).

### Mimari kurallar

- **Her servisin kendi veritabanı vardır.** Bir servis başka bir servisin veritabanına doğrudan bağlanmaz; veriyi REST ile ister veya olaylardan kendi kopyasını tutar.
- **İletişim türleri:**
  - Senkron: REST (istemci → gateway → servis, servisten servise anlık sorgu)
  - Asenkron: Amazon SNS + SQS (olaylar). Bir servis, olayı dinleyen servisin ayakta olmasını beklememeli.
  - Gerçek zamanlı: Sadece messaging servisinde WebSocket. Birden fazla pod arası dağıtım Redis Pub/Sub ile.
- Olay şemaları `shared/events/` altında JSON Schema olarak tanımlanır ve her iki dil tarafından da kullanılır. Olay adları İngilizce, PascalCase, geçmiş zaman (`StudentAddedToClassroom`). Kurallar `shared/events/README.md`'de.
- **Olay yayını (outbox):** Olay, iş değişikliğiyle aynı veritabanı transaction'ında outbox tablosuna yazılır, ayrı bir zamanlanmış görev SNS'e gönderir. Her üretici servisin kendi topic'i vardır (`<servis>-events`); olay tipi `eventType` mesaj özniteliğindedir, `traceparent` özniteliği izi tüketiciye taşır. Teslimat en az bir kezdir: tüketici `eventId`'yi saklayıp tekrarları atar.
- Kullanıcı kimliği tüm servislerde Keycloak'ın `sub` değeridir.
- **Token doğrulama hem gateway'de hem serviste yapılır.** Servisler token'ı Keycloak'ın JWKS adresiyle kendisi doğrular (imza, issuer, `aud=dershane-api`); her istekte identity servisine sorulmaz. Gateway'in eklediği başlıklara güvenilmez, gateway değişse de servis kodu değişmez. CORS ve hız sınırlama yalnızca gateway'dedir. Yetkilendirme (şube kapsamı, veli–çocuk, öğretmen–sınıf) her zaman serviste yapılır.
- Roller token'daki `realm_access.roles` claim'inden okunur: `student`, `teacher`, `guardian`, `admin`, `super-admin`. Token'da kullanıcının şubesi `branch_id` claim'i olarak bulunur (Keycloak kullanıcı özniteliği, yalnızca user servisi yazar).
- Uzun süren işler (optik dosya işleme) istek içinde değil, arka planda çalışır.
- Tekrarlı görev üretimi, gecikme işaretleme ve hatırlatmalar Kubernetes CronJob ile yapılır.
- Öğrencilerin çoğu reşit değildir: mesajlaşma kayıtları denetlenebilir tutulur, kişisel veriler loglanmaz (KVKK).

## Kod ve dil kuralları

- **Kod İngilizcedir:** sınıf, metot ve değişken adları, veritabanı tablo ve kolonları, REST yolları, JSON alanları ve olay adları. Loglar ve kod yorumları da İngilizcedir.
- **Kullanıcıya dönen hata metni Türkçedir.** Hatalar RFC 9457 ProblemDetail olarak döner: İngilizce sabit `errorCode` (`STUDENT_NUMBER_TAKEN`) ve `messages_tr.properties`'ten Türkçe `detail`. `Accept-Language: en` İngilizce metin döndürür.
- **Servis adları İngilizcedir** ve klasör, topic, Helm chart gibi tüm altyapı adlarında aynen kullanılır (`services/user`, `user-events`).
- Diyagramlar Türkçedir; koddaki karşılıkları aşağıdaki sözlükte. Diğer servislerin terimleri o servis yazılırken eklenir.

| Diyagram | Kod | | Diyagram | Kod |
|---|---|---|---|---|
| Sube | `Branch` | | Sinif | `Classroom` |
| Kullanici | `User` (tablo `users`) | | SinifUyeligi | `ClassroomMembership` |
| Ogrenci | `Student` | | OgretmenSinifAtamasi | `TeacherAssignment` |
| Ogretmen | `Teacher` | | Ders | `Subject` |
| Veli | `Guardian` | | Yakinlik | `Relationship` |
| VeliOgrenci | `StudentGuardian` | | SinavHedefi | `TargetExam` |
| GidenOlay | `OutboxEvent` | | Rol (YONETICI) | `Role` (ADMIN) |

**Java yığını (tüm Java servisleri):** Spring Boot 4, Java 25, Maven wrapper, Lombok, MapStruct, springdoc-openapi (Swagger UI), Spotless (google-java-format) + Checkstyle, JaCoCo, CycloneDX (SBOM), ArchUnit + Spring Modulith (mimari testleri).

- Lombok: entity'lerde `@Data` ve `@EqualsAndHashCode` kullanılmaz (`lombok.config` engeller). Entity `toString`'i yalnızca id yazar.
- MapStruct: `unmappedTargetPolicy=ERROR`; unutulan alan derleme hatasıdır.
- Kişisel veri taşıyan DTO record'ları `toString`'i maskeler; bunu doğrulayan test vardır.
- Swagger UI yalnızca `local` ve `dev` profillerinde açıktır. Swagger, Keycloak'a `dershane-swagger` istemcisiyle (PKCE) bağlanır.
- Oluşturma yapan POST uç noktaları isteğe bağlı `Idempotency-Key` başlığını destekler: aynı anahtar ve gövdeyle gelen tekrar istek ilk yanıtı döner, yeni kayıt açmaz.

### Java servislerinin yapısı (hexagonal)

Tüm Java servisleri (user, task, attendance, messaging, file) aynı iskeleti kullanır: ports & adapters, use case başına arayüz. `services/user` bu yapının şablonudur. **api-gateway bunun dışındadır:** domain'i yoktur, düz paket yapısı kullanır (aşağıdaki "Gateway servisi kuralları"); MapStruct, ArchUnit ve Spring Modulith kullanmaz.

```
com.dershane.<servis>
├── shared/               paylaşılan çekirdek ve platform (hata modeli, Actor, outbox, güvenlik, web)
└── <modül>/              her iş modülünde aynı üç katman
    ├── domain/           saf Java: kurallar, değer nesneleri
    ├── application/      port/in (use case + komut/sorgu + view), port/out, service
    └── adapter/          in/web (controller, dto/), in/event, out/persistence, out/keycloak ...
```

- **domain:** Spring, JPA ve Jackson kullanmaz (SLF4J ve Lombok serbest). İş kuralları buradadır; reddeden her kural `Rejections.reject(log, ...)` ile loglar ve `DomainException` fırlatır. Domain nesneleri `id` ve `version` taşır; JPA entity'leri değildir.
- **application:** Her use case bir in-port arayüzüdür: yazma için `...UseCase`, okuma için `...Query`; girdi komut record'u, çıktı view record'udur. Bir özelliğin servisi birden çok in-port'u uygular. Servisler (`@Service`, `@Transactional`) paket-özeldir; modülün dışa açık API'si yalnızca `domain` ve `application/port/in`dir. Veritabanı, Keycloak, SNS gibi her dış bağımlılık bir out-port arkasındadır.
- **Çağıran:** `Actor` (id, roller, şube) yetki kararı veren her use case'in ilk parametresidir. Controller onu `@CurrentActor Actor actor` ile alır (JWT'den çözülür); use case'ler `SecurityContextHolder`'a dokunmaz.
- **Okuma ve yazma ayrı:** Komutlar domain nesnesini yükler, kuralı çalıştırır, kaydeder. Listeleme ve görüntüleme `*QueryPort`'tan doğrudan view record'ları döner; domain'e yalnızca okuma için alan eklenmez.
- **adapter:** Controller'lar yalnızca in-port arayüzlerine bağımlıdır. DTO'lar `adapter/in/web/dto/` altında, her record ayrı dosyada. Persistence'ta `*JpaEntity` sınıfları domain'den ayrıdır; eşleme MapStruct iledir. Kaydederken var olan entity yüklenir, domain nesnesiyle güncellenir; `version` uyuşmazsa `CONCURRENT_MODIFICATION`. Adapter'lar birbirini çağırmaz.
- **Hatalar:** `ErrorCode` HTTP bilmez, her kodun bir kategorisi vardır (`NOT_FOUND`, `CONFLICT`, `RULE_VIOLATION`, `FORBIDDEN`...). Kategoriyi HTTP durumuna `ErrorStatusMapper` çevirir.
- **Modül bağımlılığı tek yönlüdür, döngü yoktur.** `user` servisinde: `enrollment` → `user`, `classroom`, `subject`; `classroom` → `branch`; `user` → `branch`, `subject`; hepsi → `shared`; `shared` hiçbir iş modülünü bilmez. Bir modül başkasını yalnızca onun `domain` ve `port/in` paketleri üzerinden çağırır. Ters yöndeki ihtiyaç port'la çözülür: port'u ihtiyaç duyan modül tanımlar, veriyi tutan modül uygular (`BranchUsagePort`, `ClassroomEnrollmentPort`, `StudentRelationsPort`). Bir modülün başka modülün tablosuna yazması yasaktır; yalnızca okuma sorguları entity adıyla tablo birleştirebilir.
- **Uygulama içi olaylar** (`UserDeactivated`, Spring `ApplicationEventPublisher`, aynı transaction'da) entegrasyon olaylarından (outbox → SNS) ayrıdır ve servisten dışarı çıkmaz.
- **Her build'de denetlenir:** `ArchitectureTest` (ArchUnit) katman kurallarını, `ModularityTest` (Spring Modulith) modül sınırlarını ve döngüleri doğrular; modül diyagramlarını `target/spring-modulith-docs` altına yazar (rapor için). Yeni modül `package-info.java`'da `@ApplicationModule(allowedDependencies = ...)` ile izin verilen bağımlılıkları bildirir; başkasının kullandığı paketler `@NamedInterface` ile işaretlenir.

## Gateway servisi kuralları

- **Varyant:** Spring Cloud Gateway **WebFlux** (`spring-cloud-starter-gateway-server-webflux`, ayarlar `spring.cloud.gateway.server.webflux.*`). Gerekçe: hazır Redis `RequestRateLimiter`, YAML ile rota ve CORS, messaging'in WebSocket trafiği için olgun proxy. Düz paket yapısı: `security/`, `web/`, `ratelimit/`.
- **Yalnızca kimlik doğrular, yetkilendirmez.** Token'ı JWKS ile (imza, issuer, `aud=dershane-api`) doğrular; rollere bakmaz ve **hiçbir kimlik başlığı eklemez**. `Authorization` ve `traceparent` başlıkları servise olduğu gibi gider. `/api/**` dışındaki yollar sunulmaz; yönetim uç noktaları 8081'de, ingress'e kapalıdır.
- **Rotalar:** Her servis üst düzey kaynak adlarını `Path` listesinde **tek tek** yazar; `/api/v1/**` gibi toptan bir kural yoktur. Yeni servis kendi rotasını ekler, mevcutlara dokunmaz. Yol öneki soyulmaz (servisler zaten `/api/v1/...` sunar). İki servis aynı kaynak adını paylaşacaksa daha özel yol önce gelen rotada yazılır.
- **Hız sınırı:** Redis tabanlı, kullanıcı başına (`sub`), varsayılan saniyede 20 istek ve en çok 40'lık ani artış; tüm rotalara `default-filters` ile uygulanır. Redis'e ulaşılamazsa sınırlayıcı isteği geçirir (fail-open) ve readiness'a Redis girmez; pod trafikten çıkmaz. Geçiş ancak Redis zaman aşımından sonra olur (`REDIS_TIMEOUT`, varsayılan 500 ms; Lettuce'un 60 sn'lik varsayılanı her isteği bir dakika bekletir). Her başarısız Redis çağrısını Spring Cloud Gateway kendisi ERROR olarak loglar (`RedisRateLimiter`: "Error calling rate limiter lua"); bu uyarı için alarm kurulmalıdır. Sınır aşılırsa 429 döner. Kimliksiz trafik JWT'de reddedildiği için sınırlanmaz.
- **Hatalar:** Gateway'in kendi ürettiği hatalar (401, 403, 404 rota yok, 429, 503, 504, 500) servislerle aynı RFC 9457 ProblemDetail biçimindedir: `errorCode` + Türkçe `detail` (`Accept-Language: en` → İngilizce). Servislerin kendi hata yanıtlarına dokunulmaz. Bağlantı reddi 503, zaman aşımı 504'tür. Her ret sebebiyle loglanır (WARN; beklenmeyen hata ERROR).
- **CORS yalnızca burada** yanıtlanır (`CORS_ALLOWED_ORIGINS`). Preflight (`OPTIONS`) token istemez.
- **Yerelde** gateway `:8000`, yönetim `:8002`dir (`:8001` exam-analysis container'ının); servislere doğrudan erişim sürer ama servis yine JWT ister.

## User servisi kuralları

- **Hesap açma:** Kullanıcıyı user servisi Keycloak Admin API ile açar; Keycloak'ın `sub` değeri `User.id` olur. Keycloak çağrısı veritabanı transaction'ının dışında yapılır. Veritabanı adımı başarısız olursa Keycloak hesabı silinerek telafi edilir; telafi de başarısız olursa ERROR logu düşülür ve `identity.compensations{outcome=failure}` metriği artar.
- **Giriş:** E-posta isteğe bağlıdır. Kullanıcı adı e-posta varsa e-posta, yoksa öğrenci için `{şubekodu}-{öğrencino}`, veli için telefon numarasıdır. E-postalı kullanıcı şifresini Keycloak'ın gönderdiği e-postayla belirler. E-postasız kullanıcıya servis geçici şifre üretir; şifre yanıtta bir kez döner, hiçbir yerde saklanmaz ve loglanmaz, ilk girişte değiştirilir. Öğretmen ve yönetici için e-posta, veli için telefon zorunludur.
- **Tablolar:** Kalıtım ayrı tablolarla (JPA JOINED): `users` + `student` / `teacher` / `guardian`. Yönetici, `role = ADMIN` olan düz kullanıcıdır.
- **Veritabanı adı:** Yerelde veritabanı ve rol adı `user_service`tir; `user` PostgreSQL'de ayrılmış sözcük olduğu için kullanılmaz.
- **Şube:** Her kullanıcı bir şubeye bağlıdır. `admin` yalnızca kendi şubesini yönetir. `super-admin` (admin ile birlikte verilen ek rol) tüm şubeleri yönetir, şube açar, ders kataloğunu ve yöneticileri yönetir. `super-admin` rolü Keycloak'ta verilir, API üzerinden verilmez.
- **Sınıf:** Öğrencinin aynı anda tek aktif sınıfı olur. Yeni sınıfa eklenince eski üyelik o günün tarihiyle kapanır; önce `StudentRemovedFromClassroom`, sonra `StudentAddedToClassroom` yayınlanır. Öğrencinin seviyesi sınıfın seviyesiyle aynı olmalıdır. Öğrenci bir sınıftayken seviyesi değiştirilemez. Üyelikler silinmez, kapatılır. Sınıf üyeliği ve öğretmen ataması `enrollment` modülündedir.
- **Veli:** Öğrencinin en fazla bir velisi olur; öğrenci velisiz kaydedilebilir, bir velinin birden çok çocuğu olabilir. Başka veli bağlanırsa eski bağ kaldırılır (`GuardianUnlinked`, sonra `GuardianLinked`). Veli ve öğrenci aynı şubede olmalıdır.
- **Pasife alma:** Kayıtlar silinmez. Kullanıcı pasife alınınca önce Keycloak girişi kapatılır, sonra profil pasif olur; öğrencinin aktif sınıf üyeliği kapanır. Üyelik, `user` modülünün aynı transaction'da yayınladığı `UserDeactivated` olayını `enrollment` dinleyerek kapanır. Aktif sınıfı olan şube, aktif öğrencisi olan sınıf pasife alınamaz. Kullanıcı kendini pasife alamaz.
- **Görünürlük:** Bir kullanıcıyı kendisi, şubesinin yöneticisi, super-admin, (öğrenciyse) aktif sınıfının öğretmeni ve velisi görebilir.
- **Öğrenci listesi:** `GET /api/v1/students` sınıfa göre süzülmez (`user` modülü sınıf verisini bilmez). Bir sınıfın öğrencileri `GET /api/v1/classrooms/{id}/students` ile alınır.

## Messaging servisi kuralları

- **Kaynak her zaman sunucudur** (PostgreSQL). Mesaj önce veritabanına yazılır, sonra Redis Pub/Sub ile dağıtılır. Pub/Sub kayıp mesajı tekrar göndermez; kaçırılan mesaj istemcinin yeniden bağlanınca yaptığı sorguyla tamamlanır. Mesajlar fiziksel silinmez (`silindi = true`). WhatsApp gibi mesajlar yalnızca kullanıcının cihazında tutulmaz; kayıtlar denetlenebilir kalmalıdır.
- **Sıralama: `Mesaj.sira`.** Sohbet başına artan `long` sayaçtır. `Sohbet.sonSira` o sohbette verilen son numarayı tutar. Mesaj kaydedilirken aynı transaction içinde `UPDATE sohbet SET son_sira = son_sira + 1 WHERE id = ? RETURNING son_sira` ile üretilir. Okuyup sonra yazma ve PostgreSQL `SEQUENCE` kullanılmaz: `SEQUENCE` numara sırası ile commit sırasını ayırır ve istemci arada bir mesajı kalıcı olarak kaçırabilir. Transaction kısa tutulur; Redis yayını, dosya işlemi gibi yavaş işler transaction dışında yapılır.
- **İstemci önbelleği.** Tarayıcı (IndexedDB) her sohbetin son N mesajını saklar (N yapılandırılabilir, başlangıç değeri 200). Sohbet açılırken ve WebSocket yeniden bağlanırken tek sorgu atılır: istemcinin bildiği son `sira` değerinden büyük yeni mesajlar ve `guncellenmeZamani` son bağlantıdan sonra olan (düzenlenen veya silinen) mesajlar. Canlı mesajlar zaten WebSocket'ten geldiği için her mesaj için sorgu atılmaz. İstemci numaralarda boşluk görürse eksik aralığı ister.
- **Okundu bilgisi.** `SohbetKatilimcisi.sonOkunanSira` katılımcının okuduğu son `sira` değeridir (0 = hiçbiri okunmadı). Okunmamış sayısı, `sira > sonOkunanSira` olan ve göndereni kendisi olmayan mesajların sayısıdır. Okundu bilgisi göndericiye aynı Pub/Sub yoluyla iletilir.
- **Düzenleme ve silme.** `duzenlenme` kullanıcıya "düzenlendi" göstermek içindir. `guncellenmeZamani` ise düzenleme veya silme dahil her değişiklikte güncellenir ve önbelleği eşitlemek için kullanılır. Silinen mesaj önbellekten de kaldırılır.
- **KVKK ve önbellek.** Çıkış yapılınca önbellek silinir. Fotoğraflar önbelleğe alınmaz, her seferinde `file` servisinden istenir. Mesaj içeriği hiçbir logda geçmez.

## Exam-analysis servisi kuralları

- Her deneme için soru → ders → konu → doğru cevap eşleştirmesi (cevap anahtarı) önceden yüklenir. Konular müfredata göre sabit bir katalogdan seçilir.
- Net = Doğru − Yanlış ÷ 4 (YKS). LGS'de bölen 3'tür; sınav türüne göre yapılandırılır, kodda sabit yazılmaz.
- Hesaplamalar pandas ile vektörel yapılır, satır satır döngü kurulmaz.

### Puanlama

- ÖSYM ve MEB puanı **soru bazlı değil, test bazlı standart puanla** hesaplar: standart puan = 50 + 10 × (net − ortalama) ÷ standart sapma. Sonra testler puan türü ağırlıklarıyla (`ScoreTypeWeight`) toplanır. Bir sorunun puanı, onu kaç kişinin doğru yaptığına göre değişmez; zor geçen testte aynı netin puan değeri artar.
- Her sınav türünün bir veya daha fazla puan türü (`ScoreType`) vardır: TYT ve LGS'de tek, AYT'de SAY/EA/SÖZ, YDT'de DİL. Ağırlıklar ders üzerinde değil, puan türü–ders çiftinde tutulur.
- Standart puan için testin ortalaması ve standart sapması gerekir. Bunlar `ExamSubjectStatistic`te tutulur ve iki kaynaktan gelir:
  - `INSTITUTIONAL`: optik dosya işlenince dershane öğrencilerinden otomatik hesaplanır.
  - `NATIONAL`: yayınevi veya sınav organizasyonu açıkladıktan sonra **yalnızca yönetici** elle girer.
- Hangi istatistiğin kullanılacağını `MockExam.scoringSource` belirler; deneme oluşturulurken seçilir. Öğrenci denemesi başına her puan türü için tek puan görür (`StudentScore`).
- Türkiye geneli bir denemede ülke istatistiği henüz girilmediyse `StudentScore.estimatedScore` boş kalır; net ve konu analizi yine görünür. İstatistik girilince puan otomatik hesaplanır.
- Tahmini puan her yerde "tahmini" olarak etiketlenir ve kaynağı belirtilir: "tahmini (kurum içi)" veya "tahmini (Türkiye geneli)". Kurum içi puan Türkiye geneliyle karşılaştırılamaz.

### Zayıf konu

- Zayıf konu tek denemeye değil, öğrencinin son N denemesine bakılarak belirlenir. Bu denemelerdeki bir konunun tüm soruları toplanır; başarı oranı eşiğin altındaysa konu zayıftır.
- N ve başarı oranı eşiği `WeakTopicSetting`te şube başına tutulur ve **yalnızca yönetici** değiştirir. Eşikler kodda sabit yazılmaz. Şubede ayar yoksa hesaplama uyarı logu ve metrikle atlanır.
- Pencere yalnızca **açıklanmış** (`PUBLISHED`) denemelerden ve **konunun sınav türü içinden** oluşur: açıklanmamış deneme öğrenciye zayıf konu olarak sızmaz, TYT ve AYT denemeleri birbirinin penceresini doldurmaz.
- Sonuç `StudentTopicStatus` özetinde tutulur. Özet deneme açıklanınca, açıklanmış denemede geç eşleştirme veya soru iptali olunca ve ayar değişince `TopicResult` kayıtlarından baştan hesaplanır; eski deneme pencereden çıkınca değer geri çıkarılmaz.
- Alt konular `Topic.parentTopicId` ile üst konuya toplanır. Zayıf çıkan konulardan öğrenci ve öğretmene "şu konuları çalış" tavsiyesi üretilir (en zayıf önce).

### Optik dosya içe aktarma

- İki kaynak vardır: optik okuyucu cihazın düz metin dosyası ve fotoğraftan okuma. Görüntü işleme exam-analysis'te yapılmaz; omr servisi fotoğraflardan `omr-canonical-v1` formatında satır üretir ve exam-analysis iki kaynağı aynı hatla işler.
- Satırdaki öğrenci numarası `StudentReplica` ile eşleştirilir. Eşleşmeyen satırlar atılmaz, nedeniyle (`STUDENT_NOT_FOUND`, `DUPLICATE_STUDENT`, `INVALID_BOOKLET`, `MALFORMED_LINE`) `UnmatchedRow` olarak saklanır.
- Yönetici eşleşmeyen satırı elle bir öğrenciye bağlayabilir; yalnızca denemenin şubesindeki öğrenciler seçilebilir. Aynı öğrenciye aynı denemede ikinci satır bağlanamaz. Kimin bağladığı veya yok saydığı kaydedilir. Satır hatalıysa `IGNORED` işaretlenir, silinmez.
- Bağlama sonrası öğrencinin sonucu üretilir, `INSTITUTIONAL` istatistik, tüm puan ve sıralar yeniden hesaplanır.
- Sonuçlar açıklanmış denemede de geç bağlama yapılabilir. Bu durumda `ExamResultsPublished` yeniden yayınlanmaz; yalnızca o öğrenci için `StudentResultAdded` yayınlanır ve notification servisi öğrenciye (ve velisine) haber verir. Açıklanmış denemeye yeni dosya yüklenemez.
- İptal edilen soru (`AnswerStatus.CANCELLED`) ÖSYM uygulamasındaki gibi doğru/yanlış/boş ve konu toplamlarına girmez. Sonuçlar saklanan cevaplardan yeniden puanlanır; olay yeniden yayınlanmaz.
- Servisler arası iletişim (4. hafta) gelene kadar geçici adaptörler kullanılır: `StudentReplica` yönetici endpoint'iyle doldurulur, optik dosya doğrudan exam-analysis'e yüklenir.

## Depo yapısı

Uygulama kodu monorepo, dağıtım tanımları ayrı GitOps deposu.

```
dershane-app/
├── services/<servis-adi>/     her servis kendi Dockerfile'ı ve testleriyle
├── frontend/
├── shared/events/             olay şemaları (JSON Schema) ve kuralları
├── infra/terraform/           AWS ve Floci altyapısı
├── local/                     yerel ortam: docker-compose.yml (PostgreSQL, Keycloak, Floci,
│                              Mailpit), keycloak/ realm tanımı, postgres/ ve floci/ init betikleri
└── .github/workflows/         servis başına pipeline + ortak (reusable) şablonlar

dershane-gitops/               ArgoCD bu depoyu izler
├── charts/<servis-adi>/       Helm chart'ları
└── environments/{dev,prod}/values.yaml
```

Servis klasör adları İngilizce, küçük harf ve tire ile ayrılmıştır (`exam-analysis`, `messaging`).

## CI/CD

- **CI: GitHub Actions.** Her servisin kendi workflow'u vardır ve `paths` filtresiyle yalnızca o servisin klasörü (veya `shared/events/`) değişince çalışır. Bir servisteki değişiklik diğerlerini derlememeli.
- **CD: ArgoCD (GitOps).** CI kümeye doğrudan dağıtım yapmaz; GitOps deposundaki imaj sürümünü günceller, küme içinde çalışan ArgoCD dağıtımı yapar.

Servis pipeline aşamaları:
1. Lint (Java: Spotless + Checkstyle, Python: Ruff)
2. Birim testleri + kapsam raporu
3. Entegrasyon testleri (Testcontainers + Floci; Java'da `*IT` sınıfları, `mvn verify`)
4. Docker imajı oluşturma + güvenlik taraması (Trivy) + SBOM (CycloneDX)
5. İmajı ECR'a gönderme (yalnızca ana dal)
6. GitOps deposunda imaj sürümünü güncelleme (yalnızca ana dal)

Pull request'te 1–4, ana dala birleştirmede 1–6 çalışır. Terraform için ayrı pipeline vardır: PR'da `plan`, onayla `apply`.

İmaj etiketi commit SHA'sıdır; `latest` kullanılmaz.

## Test

| Tür | Java | Python |
|---|---|---|
| Birim | JUnit 5 + Mockito | pytest + pytest-mock |
| API katmanı | Spring Boot Test (MockMvc) | FastAPI TestClient |
| Entegrasyon | Testcontainers | testcontainers-python |
| Kapsam | JaCoCo | pytest-cov |
| Mimari | ArchUnit (katmanlar) + Spring Modulith (modül sınırları) | |
| Yük | k6 (dilden bağımsız, tüm servisler için) | |

Entegrasyon testleri gerçek AWS'ye bağlanmaz; Floci ve Testcontainers kullanılır.

## Gözlemlenebilirlik

- Metrik: Prometheus + Grafana. Her servis `/metrics` (Java: Micrometer, Python: prometheus-client) sunar. Tüm metriklerde `application=<servis>` etiketi bulunur.
- Log: Loki. Loglar JSON formatında (Java: Spring Boot yerleşik ECS formatı), kişisel veri içermeden yazılır. Bağlam değerleri ayrı alan olarak yazılır (SLF4J `addKeyValue`); traceId ve spanId her satırda bulunur.
- **Sessiz guard clause yoktur.** Bir isteği reddeden veya işi atlayan her koşul sebebiyle loglanır: iş kuralı ve yetki reddi WARN, beklenmeyen hata ERROR, iş bulamayan zamanlanmış görev DEBUG. Reddi atan yer loglar, merkezi hata yakalayıcı aynı hatayı tekrar loglamaz.
- **Loglarda yalnızca kimlikler (UUID) bulunur.** Ad, soyad, e-posta, telefon, öğrenci no, şifre, istek gövdesi ve olay yükü loglanmaz. JDBC sürücüsünde `logServerErrorDetail=false` ayarı, SQL hatalarına kolon değerlerinin girmesini engeller.
- İz (trace): OpenTelemetry (OTLP). Olaylarla birlikte `traceparent` taşınır, böylece bir istek servisler arasında izlenebilir.
- Her servis Kubernetes için liveness ve readiness uç noktası sunar. Readiness yalnızca servisin kendi veritabanına bakar; Keycloak veya SNS kesintisi pod'u trafikten çıkarmaz.
- Java servislerinde yönetim uç noktaları ayrı portta (8081) çalışır ve ingress'e açılmaz: `/metrics`, `/health/liveness`, `/health/readiness`, `/info` (commit SHA).
- exam-analysis servisi HPA ile ölçeklenir; yük testi bu senaryoyla yapılır.

## Başarı kriterleri

- Servisler birbirinden bağımsız dağıtılabilir
- Ana dala birleştirilen değişiklik, elle müdahale olmadan AWS'deki kümeye ulaşır
- Küme sıfırdan Terraform ve otomasyon betikleriyle, elle müdahale olmadan kurulabilir
- Aynı Terraform kodu Floci ve AWS'de hatasız uygulanır
- Yük testinde exam-analysis pod sayısı HPA ile artar ve yük kalkınca azalır
- Bir servis kapatıldığında diğer işlevler çalışmaya devam eder
- Tüm servislerin metrik, log ve izleri Grafana'da görülebilir
- DORA metrikleri (dağıtım sıklığı, teslim süresi, geri yükleme süresi, değişiklik hata oranı) ölçülüp raporlanır

## Takvim (11 hafta)

| Hafta | İş |
|---|---|
| 1 | Servis analizi, ilk kod, CI/CD pipeline |
| 2–3 | Backend servisleri |
| 4 | Floci üzerinde servisler arası iletişim |
| 5 | Vize haftası (çalışma yok) |
| 6–7 | Frontend |
| 8–9 | AWS altyapısı ve EC2 üzerinde Kubernetes (kubeadm) kurulumu |
| 10 | İzleme (monitoring) |
| 11 | Genel revizyon, testler, final raporu |

## Açık kararlar

Bunlar netleşince bu bölümden çıkarılıp yukarıya işlenmelidir.

- **Üretimde ilk super-admin kurulumu:** Yerelde realm JSON'u ve `db/local` tohum verisi var; üretimde nasıl yapılacağı AWS haftasında (8–9) belirlenecek.
- **exam-analysis'teki `Ders` ile user'daki `Subject` ilişkisi:** exam-analysis'te sınav türüne bağlı, ağırlıklı ayrı bir ders kataloğu var; iki katalog exam-analysis servisi yazılırken eşleştirilecek.
- **Tahmini puan ölçekleme formülü:** Ağırlıklı standart puanların TYT, AYT ve LGS için hangi aralığa ve nasıl ölçekleneceği (taban puan dahil) ÖSYM ve MEB kılavuzlarından doğrulanıp puan türüne (`ScoreType.baseScore`, `ScoreTypeWeight`) işlenmeli. `services/exam-analysis/seed/catalog.json` içindeki ağırlıklar şimdilik yer tutucudur (`weightsVerified: false`).
- **Fakülte/bölüm adı ve danışman unvanı:** Rapor kapağı için eksik.

## Rapor

1. ara raporun (proje önerisi ve planlama) teslim tarihi 15.10.2026. Rapordaki literatür ve mimari, bu dosyadaki kararlarla tutarlı tutulmalı. Mimari bir karar değiştiğinde rapordaki servis tablosu, teknoloji tablosu ve mimari diyagram da güncellenir.
