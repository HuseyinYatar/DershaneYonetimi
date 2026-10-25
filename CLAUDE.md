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
| api-gateway | Tek giriş noktası, yönlendirme, JWT doğrulama, hız sınırlama | Java (Spring Cloud Gateway) | — | — |
| kimlik | Giriş, token, roller | **Keycloak** (önerilen, bkz. Açık kararlar) | PostgreSQL | — |
| kullanici | Öğrenci/öğretmen/veli profilleri, sınıf, şube, veli–öğrenci eşleşmesi | Java (Spring Boot) | PostgreSQL | Yayınlar: `StudentAddedToClass` |
| exam-analysis (deneme analizi) | Optik dosya içe aktarma, puanlama, konu analizi, gelişim raporları | Python (FastAPI + SQLAlchemy + pandas) | PostgreSQL, Redis | Yayınlar: `ExamResultsPublished`, `StudentResultAdded`; Dinler: `StudentAddedToClass` |
| omr (planlandı) | Fotoğraflanmış/taranmış optik formlardan işaretleri okur, kanonik satır dosyası üretir | Python (FastAPI + OpenCV) | PostgreSQL, S3 (dosya servisi üzerinden) | Yayınlar: `AnswerSheetsRecognized` |
| mesajlasma | WebSocket sohbet, fotoğraflı soru, okundu bilgisi | Java (Spring Boot) | PostgreSQL, Redis Pub/Sub | Yayınlar: `YeniMesaj` (alıcı çevrimdışıysa) |
| gorev | Görev/ödev atama, tekrarlı görevler, teslim ve değerlendirme | Java (Spring Boot) | PostgreSQL | Yayınlar: `GorevAtandi`, `GorevYaklasiyor`, `GorevGecikti`, `TeslimDegerlendirildi`; Dinler: `StudentAddedToClass` |
| yoklama | Derse katılım, devamsızlık | Java (Spring Boot) | PostgreSQL | Yayınlar: `OgrenciDevamsiz` |
| bildirim | Olayları dinleyip e-posta, SMS, uygulama içi bildirim gönderir | Python (FastAPI) | PostgreSQL | Dinler: yukarıdaki olaylar |
| dosya | Fotoğraf, ödev, materyal yükleme/indirme | Java (Spring Boot) | S3 | — |

Frontend: Next.js (React, TypeScript).

### Mimari kurallar

- **Her servisin kendi veritabanı vardır.** Bir servis başka bir servisin veritabanına doğrudan bağlanmaz; veriyi REST ile ister veya olaylardan kendi kopyasını tutar.
- **İletişim türleri:**
  - Senkron: REST (istemci → gateway → servis, servisten servise anlık sorgu)
  - Asenkron: Mesaj kuyruğu (olaylar). Bir servis, olayı dinleyen servisin ayakta olmasını beklememeli.
  - Gerçek zamanlı: Sadece mesajlasma servisinde WebSocket. Birden fazla pod arası dağıtım Redis Pub/Sub ile.
- Olay şemaları `shared/events/` altında JSON Schema olarak tanımlanır ve her iki dil tarafından da kullanılır. Olay adları İngilizce, PascalCase; alanlar camelCase. Diğer servislerin hâlâ Türkçe olan olay adları, servis yazılırken İngilizceye çevrilir.
- **Dil:** Kod, servis adları, veritabanı tabloları, API alanları, olay adları ve Keycloak realm rolleri (`STUDENT`, `TEACHER`, `PARENT`, `ADMIN`) İngilizcedir. UML ve rapor Türkçe kalır; her servisin README'sinde Türkçe → İngilizce sözlük tutulur.
- Olay yayınlayan servisler **transactional outbox** kullanır: olay, değişiklikle aynı transaction'da `outbox_event` tablosuna yazılır, worker SNS'e gönderir. Teslimat en az bir kezdir; tüketiciler `eventId` ile tekrarı ayıklar (`processed_event`).
- Kullanıcı kimliği tüm servislerde Keycloak'ın `sub` değeridir.
- Servisler token'ı Keycloak'ın JWKS adresiyle kendisi doğrular; her istekte kimlik servisine sorulmaz. Roller token içindeki claim'lerden okunur. Öğretmen ve yönetici yalnızca token'daki `branch_id` claim'inin şubesinde işlem yapar.
- Uzun süren işler (optik dosya işleme) istek içinde değil, arka planda çalışır. exam-analysis'te iş tablosu kuyruk olarak kullanılır (`SELECT … FOR UPDATE SKIP LOCKED`); worker aynı imajdan ayrı Deployment olarak çalışır ve HPA ile ölçeklenir.
- **Sessiz guard clause kullanılmaz:** reddedilen her işlem kodlu bir domain hatası fırlatır ve tek yerde loglanır; arka planda atlanan her adım nedeniyle loglanır ve metrik artırır.
- Tekrarlı görev üretimi, gecikme işaretleme ve hatırlatmalar Kubernetes CronJob ile yapılır.
- Öğrencilerin çoğu reşit değildir: mesajlaşma kayıtları denetlenebilir tutulur, kişisel veriler loglanmaz (KVKK).

## Deneme analizi kuralları

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
- Sonuçlar açıklanmış denemede de geç bağlama yapılabilir. Bu durumda `ExamResultsPublished` yeniden yayınlanmaz; yalnızca o öğrenci için `StudentResultAdded` yayınlanır ve bildirim servisi öğrenciye (ve velisine) haber verir. Açıklanmış denemeye yeni dosya yüklenemez.
- İptal edilen soru (`AnswerStatus.CANCELLED`) ÖSYM uygulamasındaki gibi doğru/yanlış/boş ve konu toplamlarına girmez. Sonuçlar saklanan cevaplardan yeniden puanlanır; olay yeniden yayınlanmaz.
- Servisler arası iletişim (4. hafta) gelene kadar geçici adaptörler kullanılır: `StudentReplica` yönetici endpoint'iyle doldurulur, optik dosya doğrudan exam-analysis'e yüklenir.

## Depo yapısı

Uygulama kodu monorepo, dağıtım tanımları ayrı GitOps deposu.

```
dershane-app/
├── services/<servis-adi>/     her servis kendi Dockerfile'ı ve testleriyle
├── frontend/
├── shared/events/             olay şemaları
├── infra/terraform/           AWS ve Floci altyapısı
├── local/docker-compose.yml   Floci + yerel bağımlılıklar
└── .github/workflows/         servis başına pipeline + ortak (reusable) şablonlar

dershane-gitops/               ArgoCD bu depoyu izler
├── charts/<servis-adi>/       Helm chart'ları
└── environments/{dev,prod}/values.yaml
```

Servis klasör adları küçük harf, tire ile ayrılmış ve İngilizce (`exam-analysis`). Henüz yazılmamış servisler yazılırken İngilizce adlarını alır.

## CI/CD

- **CI: GitHub Actions.** Her servisin kendi workflow'u vardır ve `paths` filtresiyle yalnızca o servisin klasörü (veya `shared/events/`) değişince çalışır. Bir servisteki değişiklik diğerlerini derlememeli.
- **CD: ArgoCD (GitOps).** CI kümeye doğrudan dağıtım yapmaz; GitOps deposundaki imaj sürümünü günceller, küme içinde çalışan ArgoCD dağıtımı yapar.

Servis pipeline aşamaları:
1. Lint (Java: Checkstyle, Python: Ruff)
2. Birim testleri + kapsam raporu
3. Entegrasyon testleri (Testcontainers + Floci)
4. Docker imajı oluşturma + güvenlik taraması (Trivy)
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
| Yük | k6 (dilden bağımsız, tüm servisler için) | |

Entegrasyon testleri gerçek AWS'ye bağlanmaz; Floci ve Testcontainers kullanılır.

## Gözlemlenebilirlik

- Metrik: Prometheus + Grafana. Her servis `/metrics` (Java: Micrometer, Python: prometheus-client) sunar.
- Log: Loki. Loglar JSON formatında, kişisel veri içermeden yazılır.
- İz (trace): OpenTelemetry.
- Her servis Kubernetes için liveness ve readiness uç noktası sunar.
- Deneme Analizi servisi HPA ile ölçeklenir; yük testi bu senaryoyla yapılır.

## Başarı kriterleri

- Servisler birbirinden bağımsız dağıtılabilir
- Ana dala birleştirilen değişiklik, elle müdahale olmadan AWS'deki kümeye ulaşır
- Küme sıfırdan Terraform ve otomasyon betikleriyle, elle müdahale olmadan kurulabilir
- Aynı Terraform kodu Floci ve AWS'de hatasız uygulanır
- Yük testinde Deneme Analizi pod sayısı HPA ile artar ve yük kalkınca azalır
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

- **Mesaj kuyruğu:** RabbitMQ (Kubernetes içinde, taşınabilir) veya Amazon SQS + SNS (yönetilen, Floci ile emüle edilir). Kafka bu hacim için gereğinden ağır olduğu için elendi. Rapor şu an SQS + SNS diyor. exam-analysis SNS + SQS adaptörüyle yazıldı; `EventPublisher` arayüzü sayesinde RabbitMQ'ya geçiş tek adaptör değişikliğidir.
- **Kimlik doğrulama:** Keycloak önerildi (kendi auth servisini yazmak yerine). Alternatif: Amazon Cognito. Keycloak seçilirse kullanici servisi yine iş verisi için kalır.
- **Tahmini puan ölçekleme formülü:** Ağırlıklı standart puanların TYT, AYT ve LGS için hangi aralığa ve nasıl ölçekleneceği (taban puan dahil) ÖSYM ve MEB kılavuzlarından doğrulanıp puan türüne (`ScoreType.baseScore`, `ScoreTypeWeight`) işlenmeli. `services/exam-analysis/seed/catalog.json` içindeki ağırlıklar şimdilik yer tutucudur (`weightsVerified: false`).
- **Fakülte/bölüm adı ve danışman unvanı:** Rapor kapağı için eksik.

## Rapor

1. ara raporun (proje önerisi ve planlama) teslim tarihi 15.10.2026. Rapordaki literatür ve mimari, bu dosyadaki kararlarla tutarlı tutulmalı. Mimari bir karar değiştiğinde rapordaki servis tablosu, teknoloji tablosu ve mimari diyagram da güncellenir.
