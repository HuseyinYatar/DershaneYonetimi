# k8s: kümede çalışan her şey

Ansible kümeyi kurup ArgoCD'yi bırakıyor. ArgoCD bu klasörü (main dalı) izliyor ve burada ne yazıyorsa kümeye kuruyor. Kümede elle `kubectl apply` yapılmıyor; değişiklik bu klasöre commit edilip main'e birleştirilince ArgoCD uyguluyor.

## Klasörler

```
k8s/
├── apps/              ArgoCD'ye "şunları kur" diyen liste (root Application bu klasörü izler)
│   ├── platform/      kümenin altyapısı, her biri bir ArgoCD Application
│   └── services.yaml  bizim servislerimiz: environments/prod'daki her dosya için bir Application
├── platform/          altyapının ayarları: hazır Helm paketlerinin values dosyaları ve kendi küçük tanımlarımız
├── charts/service/    8 servisin ortak Helm şablonu
└── environments/prod/ her servisin kendi ayarları (hangi parolayı ve adresi Secrets Manager'dan aldığı, kaç pod...)
```

## Kurulum sırası (wave)

Her Application'ın üzerindeki `argocd.argoproj.io/sync-wave` numarası sırayı belirliyor. ArgoCD bir numaradaki her şey sağlıklı olmadan sonrakine geçmiyor. Bunun için Ansible ArgoCD'ye Application sağlık kontrolünü ekliyor (`infra/ansible/roles/argocd/templates/argocd-values.yaml.j2`).

| Sıra | Ne | Neden bu sırada |
|---|---|---|
| 0 | External Secrets, cert-manager, metrics-server | Sonrakiler bunların getirdiği nesne türlerini kullanıyor |
| 1 | Traefik, cluster-config (`dershane` namespace'i, Secrets Manager bağlantısı, Let's Encrypt ayarı) | 0'daki programlara dayanıyor |
| 2 | db-init (RDS'te 7 veritabanı), Keycloak | Keycloak'ın veritabanı ve parolası hazır olmalı |
| 3 | 8 servis (ApplicationSet) | Veritabanları ve Keycloak hazır |
| 4 | bootstrap (super-admin'in user servisi kaydı) | user servisinin tabloları oluşmuş olmalı |
| 5 | demo-seed (demo verisi) | Her şey ayakta, super-admin giriş yapabiliyor |

Not: ApplicationSet'in kendi sağlık durumu yok, bu yüzden 4 ve 5, servisler tam açılmadan başlayabiliyor. İkisi de bunu bekleyecek şekilde yazıldı: bootstrap tabloları, demo-seed servisleri bekliyor.

## İlk kurulum

1. **Terraform'u `standby = false` ile uygula.** NLB ve Redis bu adımda açılır. Ardından Güzel Hosting'e `@` ve `*` için A kaydı (`terraform output ingress_public_ip`) ve 3 DKIM CNAME kaydı girilir.
2. **Dosyalarda doldurulacak bir şey yok.** Parolalar ve veri deposu adresleri Terraform'un yazdığı Secrets Manager kayıtlarından gelir (aşağıdaki "Parolalar"); dosyalarda yalnızca AWS hesap numarası (`423082503744`) yazılı. Başka bir hesaba kurulursa bu numara `k8s/` ve `.github/workflows/_image.yml` içinde değiştirilir.
3. **İmajlar:** CI gönderiyor (aşağıdaki "İmajlar ve etiketler"). İlk kurulumda imajların hepsi gerekiyor: GitHub'da Actions sekmesinden 9 workflow (7 servis, `frontend`, `keycloak`) main üzerinde **Run workflow** ile bir kez çalıştırılır. Her biri imajı ECR'a gönderir ve `k8s/`'teki etiketini main'e commit'ler. Bitenleri `aws ecr list-images --repository-name <servis>` ile görebilirsin.
4. **Ansible'ı çalıştır** (`infra/ansible/README.md`); son adımda ArgoCD kurulur ve gerisini kurar. Durumu `kubectl -n argocd get applications` ile ya da arayüzden (`infra/ansible/README.md`) izlersin. İlk kurulum, sertifikalar ve demo verisiyle birlikte 15–25 dakika sürer.

## İmajlar ve etiketler (CD)

Bir servisin kodu main'e birleşince o servisin workflow'u (`.github/workflows/<servis>.yml`) testleri çalıştırır, sonra `_image.yml`:
1. İmajı derler, SBOM çıkarır, Trivy ile tarar (PR'larda yalnızca bu kadar).
2. AWS'ye OIDC ile girer: GitHub'ın kısa ömürlü token'ı STS'de `dershane-prod-github-ecr-push` rolünün geçici anahtarlarıyla değiştirilir. Rol yalnızca bu reponun main dalına güveniyor ve yalnızca ECR'a imaj gönderebiliyor. GitHub'da AWS anahtarı yok.
3. İmajı `<servis>:<commit SHA>` etiketiyle ECR'a gönderir. ECR etiketleri değiştirilemez; aynı commit için yeniden çalıştırılırsa imaj zaten var diye gönderme atlanır.
4. `k8s/environments/prod/<servis>.yaml`'daki `tag:` satırını (Keycloak'ta `k8s/platform/keycloak/deployment.yaml`'daki imaj satırını) bu SHA ile değiştirip main'e commit'ler (`deploy(<servis>): <sha>`). Bu commit yalnızca `k8s/`'i değiştirdiği ve `GITHUB_TOKEN` ile atıldığı için yeni bir workflow başlatmaz.
5. ArgoCD commit'i görür ve pod'ları yeni imajla açar.

Elle yeniden dağıtmak için: Actions → servisin workflow'u → **Run workflow** (main).

## Git'te yazılı tek ortam değeri: hesap numarası

`423082503744` imaj adreslerinde, IRSA rol ARN'lerinde ve SNS topic ARN'lerinde yazılı. İmaj adresi pod açılmadan önce bilinmek zorunda ve External Secrets'ın kendi rolü Secrets Manager'dan okunamaz; bu yüzden bu numara Git'te. AWS hesap numarasını gizli bilgi saymaz, tek başına erişim vermez.

## Parolalar

Git'te parola yok. Hepsi AWS Secrets Manager'da duruyor ve External Secrets pod'lara getiriyor. Kullanıcı parolalarıyla ilgili iki değer var:

| Secrets Manager | Ne |
|---|---|
| `dershane/keycloak/superadmin` | `superadmin@onlinecourses.site`'ın ilk parolası. İlk girişte değiştirilmesi isteniyor; sonrası yalnızca Keycloak'ta |
| `dershane/demo/password` | Bütün demo hesaplarının (öğretmen, öğrenci, veli) ortak parolası |

Okumak için:
```bash
aws secretsmanager get-secret-value --secret-id dershane/demo/password --query SecretString --output text
```
Değer `{"value":"..."}` biçiminde. Konsoldan da okunabilir: Secrets Manager → kayıt → **Retrieve secret value**.

Diğer kayıtlar kullanıcı parolası değil, sistemin kendi parolaları: veritabanları, Redis, SMTP, Keycloak yönetim konsolu (`dershane/keycloak/admin`, kullanıcı `admin`), user servisinin Keycloak'a bağlanma parolası.

**Veri deposu adresleri de Git'te değil.** RDS ve Redis adresleri parola değil (ikisi de private subnet'te, yalnızca node'ların güvenlik grubuna açık), ama altyapının iç ayrıntısı ve repo public. Terraform onları `dershane/endpoints`'e (`rds_host`, `redis_host`) yazar; External Secrets bağlantı ayarlarını (`DB_URL`, `REDIS_HOST`, `DATABASE_URL`, `REDIS_URL`, db-init ve bootstrap'ta `PGHOST`, Keycloak'ta `KC_DB_URL`) buradan kurar. Git'te yalnızca `{{ .rdsHost }}` gibi yer tutucular var. Kümede adresi görmek için: `kubectl -n dershane get secret user-secrets -o jsonpath='{.data.DB_URL}' | base64 -d`.

## Demo verisi

`demo-seed` Job'u kurulumun sonunda `local/seed/seed.mjs`'i (yerelde `local/up.sh --seed` ile çalışan betik) kümenin adresleriyle çalıştırır. Veri gerçek API'ler üzerinden girildiği için olaylar akar ve her servisin kopyası dolar.

Yüklenen veri:
- **Öğretmenler:** `ogretmen@dershane.local` (Kemal Aksoy, Demo 12-A) ve `ogretmen2@dershane.local` (Seda Kılıç, Demo 12-B).
- **Öğrenciler:** her sınıfta 10, kullanıcı adları `mrkz-1001`…`mrkz-1010` ve `mrkz-1011`…`mrkz-1020`.
- **Veli:** Ayşe'nin (`mrkz-1001`) velisi, kullanıcı adı telefon numarası `05551234567`.
- **Denemeler:** 5 TYT ve 2 AYT. Son 4 haftanın yoklamaları, ödevler; Ayşe'nin bazı ödevleri teslim edilmiş ve notlandırılmış.

Demo hesaplarının e-posta adresleri gerçek değil. SES yalnızca doğrulanmış adreslere gönderdiği için e-posta bildirimleri `FAILED` görünür; uygulama içi bildirimler çalışır.

Job tekrar çalışsa veri çoğalmaz: betik her adımda önce var olana bakıyor. Super-admin parolasını değiştirdikten sonra Job tekrar çalışırsa hiçbir şey yapmadan biter; parolana dokunmaz.

## Gerçek kullanıma geçerken

1. `k8s/apps/platform/demo-seed.yaml`'ı sil.
2. Demo hesaplarını ve sınıflarını yönetim ekranından pasife al.
3. `dershane/demo/password`'ü Terraform'dan kaldır (`infra/terraform/app-secrets.tf`).

## Yeni bir servis eklemek

`k8s/environments/prod/<servis>.yaml` dosyasını ekle (örnek olarak benzer bir servisinkini al). ApplicationSet onu kendisi bulur; `apps/` ya da chart değişmez. Servisin AWS izni ve IRSA rolü Terraform'da (`infra/terraform/main.tf`, `service_access`), ECR reposu `ecr_repositories`'te açılır. Servisin workflow'u imaj işinde `_image.yml`'i (izinleriyle) çağırır; etiketi CI yazar.

## Bilinen sınırlar

- metrics-server, kubelet sertifikalarını doğrulamadan çalışıyor (`--kubelet-insecure-tls`).
- Keycloak tek pod. Servislerin çoğu tek pod; exam-analysis API'si yüke göre 1–3 pod.
- Keycloak realm dosyası yalnızca ilk açılışta içe aktarılıyor. Sonraki değişiklikler yönetim konsolundan ya da realm silinip yeniden içe aktarılarak yapılır.
- demo-seed main dalındaki betiği çalıştırıyor.
- Bir servisin entegrasyon testleri kırıksa imajı ECR'a gitmez ve kümedeki sürümü eski kalır (CI'da testler imajdan önce çalışıyor).
- Keycloak imajında bir güvenlik açığı bilerek kabul edildi: Keycloak 26.8.2'nin içindeki Guava 33.6.0 (CVE-2026-102554, hizmet aksatma). Kendi imajımızda değiştirilemiyor; `infra/keycloak/.trivyignore`'da gerekçesiyle ve 2027-01-31 bitiş tarihiyle duruyor. Düzeltilmiş Guava'lı bir Keycloak çıkınca sürüm yükseltilip satır silinir.
