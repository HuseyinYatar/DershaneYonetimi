# Terraform altyapısı

Aynı kök iki yere uygulanır:
- **Floci** (yerel geliştirme): yalnızca SNS topic'leri, SQS kuyrukları ve optik dosya bucket'ı. Servisler laptop'ta bunlarla çalışır.
- **AWS** (`eu-central-1`, free plan): bunlara ek olarak kubeadm kümesi için platformun tamamı.

Kubernetes kurulumunun kendisi burada değildir. Terraform sunucuları boş Ubuntu olarak açar ve kümenin ihtiyaç duyduğu AWS kaynaklarını kurar; node hazırlığı ve küme (`kubeadm init/join`, Calico, Argo CD) `infra/ansible`'dadır, Traefik ve uygulamalar `k8s/`'tedir.

```
infra/terraform/
├── bootstrap/            yalnızca AWS, hesap başına bir kez: state bucket'ı, Budget, GitHub OIDC (ECR push için)
├── backend.tf            backend "s3" {}; değerler environments/prod.s3.tfbackend'ten (yerelde override edilir)
├── events.auto.tfvars    topic'ler ve abonelikler (her ortamda aynı, kendiliğinden yüklenir)
├── main.tf               modül çağrıları; servislerin AWS izinleri (local.service_access)
├── environments/         local.tfvars (Floci), prod.tfvars ve prod.s3.tfbackend (AWS)
├── modules/
│   ├── events/           SNS topic, SQS kuyruk, DLQ
│   ├── storage/          S3 bucket'ları (optik dosyalar, etcd yedeği)
│   ├── network/          VPC, public/private subnet, security group'lar (NAT yok)
│   ├── nodes/            cp + worker EC2 (boş Ubuntu), control plane EIP
│   ├── iam/              node rolleri, servis izinleri, IRSA (OIDC bucket'ı, servis rolleri)
│   ├── registry/         ECR repoları, GitHub'dan push rolü
│   ├── database/         RDS PostgreSQL 17, servis başına DB secret'ı
│   ├── cache/            ElastiCache Redis (TLS + AUTH)
│   ├── edge/             NLB ve sabit Elastic IP
│   ├── email/            SES alan adı kimliği, SMTP secret'ı
│   └── monitoring/       DLQ alarmları, alarm topic'i
└── tests/                terraform test (sahte provider, AWS gerekmez)
```

`platform_enabled = false` (varsayılan) iken yalnızca `events` ve `storage` açılır. Diğer modüller yalnızca `prod` içindir.

## Yerel (Floci)

`docker compose -f local/docker-compose.yml up -d` ile `floci-init` kendiliğinden çalışır:

1. Floci'nin portu açılana kadar bekler.
2. Bu klasörün bir kopyasına `backend_override.tf` yazar (`terraform { backend "local" {} }`). Terraform `*_override.tf` dosyalarını ana dosyaların üzerine işler, böylece AWS için tanımlı `backend "s3"` bloğu yerelde yerel backend'e döner.
3. `terraform init -backend-config=path=/state/terraform.tfstate`: state dosyası `terraform-state` volume'ünde tutulur (eski kurulumla aynı yer, `down -v` gerekmez).
4. `terraform apply -var-file=environments/local.tfvars`: SNS, SQS ve optik dosya bucket'ı.

Floci adresi `AWS_ENDPOINT_URL` ortam değişkeninden gelir, `.tf` dosyalarında adres yazmaz. EC2, RDS, NLB gibi kaynaklar yerelde hiç kurulmaz (`platform_enabled = false`).

`down -v` volume'leri sildiği için Floci ile birlikte state de sıfırlanır.

## AWS: ilk kurulum

### 1. Hesap kontrolleri (konsol)

- **Free plan bitiş tarihi:** 11. haftadan sonra olmalı (Billing → Free plan).
- **Makine tipleri:** EC2 → Launch instance listesinde `c7i-flex.large` ve `m7i-flex.large` "Free tier eligible" görünmeli.
- **vCPU kotası:** Service Quotas → "Running On-Demand Standard instances" en az 6 vCPU olmalı.
- **Yönetici kullanıcı:** root kullanılmaz, yalnızca root'a MFA eklenir. Her ekip üyesine ayrı bir IAM kullanıcısı açılır (`AdministratorAccess`, MFA, konsol şifresi); kullanıcı paylaşılmaz, apply'ı kimin yaptığı CloudTrail'de bu adla görünür.

### 2. Laptop'ta giriş

Terraform yalnızca laptop'tan, elle çalıştırılır; CI AWS'ye Terraform için girmez. Kimlik AWS CLI'dan gelir:

```bash
aws login                      # AWS CLI v2.32+, tarayıcıda IAM kullanıcısıyla giriş, geçici anahtar
aws sts get-caller-identity    # arn:aws:iam::<hesap>:user/<kullanıcı> görünmeli
```

Terraform bu oturumu okuyamazsa anahtar ortama aktarılır: `eval "$(aws configure export-credentials --format env)"`. Access key (`aws configure`) yalnızca son çare: süresiz geçerlidir ve diskte düz metin durur.

### 3. bootstrap (bir kez)

```bash
export TF_VAR_alert_email='<adres>'
terraform -chdir=infra/terraform/bootstrap init
terraform -chdir=infra/terraform/bootstrap apply -var-file=environments/prod.tfvars
terraform -chdir=infra/terraform/bootstrap output
```

Çıktılar:
- **`state_bucket`:** `environments/prod.s3.tfbackend` dosyasındaki `<ACCOUNT_ID>` yerine yazılır. Ekipteki herkes aynı state'i ve aynı kilidi (`use_lockfile`) kullanır; iki kişi aynı anda apply yaparsa ikincisi kilitte bekler.

bootstrap state'i (`bootstrap/terraform.tfstate`) Git'e girmez. Kaybolmaması için bir kopyası saklanmalı ya da state bucket'ına taşınmalı (`backend "s3"` eklenip `terraform init -migrate-state`).

### 4. Kişisel değerler

Bu değerler Git'e yazılmaz, ortam değişkeni olarak verilir:

| Ortam değişkeni | Örnek |
|---|---|
| `TF_VAR_admin_cidrs` | `["<ev IP'si>/32"]` (SSH ve kubectl; boşsa yalnızca SSM) |
| `TF_VAR_ssh_public_key` | `ssh-ed25519 AAAA...` |
| `TF_VAR_alert_email` | alarm ve bütçe e-postası |
| `TF_VAR_ses_test_recipients` | `["<adres>"]` (SES sandbox) |

### 5. plan ve apply

```bash
cd infra/terraform
terraform init -backend-config=environments/prod.s3.tfbackend
terraform plan -var-file=environments/prod.tfvars -out=tfplan
terraform apply tfplan
```

`apply tfplan`, incelenen planın birebir aynısını uygular; arada state değiştiyse yeni plan çıkarmaz, hata verir. Sonraki değişikliklerde de aynı komutlar çalıştırılır. PR'da `.github/workflows/terraform.yml` yalnızca statik kontrolleri (fmt, validate, test, tflint, trivy) yapar, AWS'ye bağlanmaz.

### 6. Apply'dan sonra elle yapılacaklar

- **Güzel Hosting DNS paneli:**
  - `@` ve `*` için A kaydı → `terraform output ingress_public_ip`.
  - `terraform output ses_dkim_records` içindeki 3 CNAME kaydı.
- **Gelen e-postalar:** SNS alarm aboneliğinin onay e-postası ve SES test alıcılarının doğrulama e-postaları onaylanır.
- **SES sandbox:** sandbox'tan çıkış talebi konsoldan verilir (isteğe bağlı; sandbox'ta yalnızca doğrulanmış adreslere gönderilir).

## Küme kurulumunda Terraform çıktıları

Sunucular boş Ubuntu olarak açılır. Node hazırlığı (swap, kernel ayarları, containerd, kubelet/kubeadm/kubectl, `ecr-credential-provider`) ve küme kurulumu (`kubeadm init/join`, Calico, `keys.json`, etcd yedeği, Argo CD) `infra/ansible`'dadır; ayrıntılar `infra/ansible/README.md`'de. Ansible şu çıktıları okur:

| Çıktı | Ansible'da kullanıldığı yer |
|---|---|
| `ansible_inventory` | `inventory.sh` → `inventory.ini` (sunucular ve IP'leri) |
| `control_plane_public_ip` | API server sertifikası (`certSANs`) ve laptop kubeconfig'i |
| `oidc_issuer` | kube-apiserver `--service-account-issuer` / `--service-account-jwks-uri` (IRSA) |
| `oidc_bucket` | `keys.json`'ın yüklendiği bucket |
| `bucket_names["etcd-backups"]` | etcd snapshot'larının yüklendiği bucket |

Sürümler (Kubernetes, ecr-credential-provider, Calico, Helm, Argo CD) Terraform'da değil, `infra/ansible/group_vars/all/main.yml`'dedir. Değişince sunucu yeniden yaratılmaz; `ansible-playbook site.yml` mevcut node'lara uygular.

Diğer ayarlar:
- **Traefik:** Helm değerleri `service.type: NodePort`, `ports.web.nodePort: 30080`, `ports.websecure.nodePort: 30443` olmalı (NLB target group'larıyla aynı).
- **Pod'lar:** `service_role_arns` çıktısındaki rolü `AWS_ROLE_ARN` olarak alır, projected token'ı (audience `sts.amazonaws.com`) `AWS_WEB_IDENTITY_TOKEN_FILE` olarak bağlar.

## Secrets Manager kayıtları

| Ad | İçerik | Yazan |
|---|---|---|
| `dershane/db/<veritabanı>` (7 adet) | `username`, `password`, `dbname` | Terraform (şifre write-only, state'e girmez) |
| `rds!db-...` | RDS ana kullanıcı şifresi | RDS |
| `dershane/redis` | `password` (AUTH token) | Terraform (write-only) |
| `dershane/notification/smtp` | SES SMTP sunucusu, kullanıcı adı, şifre | Terraform (IAM access key'den türer, state'te bulunur) |
| `dershane/cluster/kubeconfig` | boş açılır; admin kubeconfig | control plane (Ansible `control_plane` rolü) |

Pod'lara External Secrets Operator ile gelirler. Veritabanlarını ve rollerini RDS içinde `k8s/` altındaki `db-init` Job'u açar.

## Açıp kapatma (kredi)

| Ne zaman | Komut | Boşta kalan |
|---|---|---|
| Günlük | `infra/scripts/power.sh off` / `on` (EC2 ve RDS) | NLB, ElastiCache, EBS, EIP |
| Uzun ara | `prod.tfvars`'ta `standby = true`, sonra plan ve apply | EBS, 2 EIP, RDS diski, secret'lar |

Terraform sunucunun açık/kapalı durumunu izlemez, bu yüzden apply `power.sh`'ı geri almaz. RDS durdurulduktan 7 gün sonra kendiliğinden açılır.

## Test

```bash
cd infra/terraform
terraform fmt -check -recursive
terraform init -backend=false && terraform validate && terraform test
```

`terraform test` sahte provider ile üç senaryoyu çalıştırır:
- `local`
- `prod` + IRSA
- `standby` + node rolü

## Bilinen sınırlar

- **NAT Gateway yok:** node'lar public subnet'te ve public IP'lidir, gelen trafiği security group sınırlar.
- **Tek AZ, tek control plane.**
- **Tek RDS instance'ı** içinde servis başına veritabanı.
- **SMTP şifresi state'te:** SES SMTP şifresi bir IAM access key'den türediği için Terraform state'inde bulunur. State bucket'ı bu yüzden şifreli, versiyonlu ve kapalıdır.
- **apply onaysızdır:** AWS yetkisi olan ekip üyesi kendi laptop'ından apply yapabilir; ikinci bir onay adımı yoktur. Kimin ne zaman değiştirdiği yalnızca CloudTrail'de görünür.
- **`trivy config` yalnızca rapor ediyor:** bulgular gözden geçirilip `.trivyignore`'a gerekçesiyle yazılana kadar CI'ı durdurmaz.
- **Floci yalnızca olay altyapısı için:** EC2/kubeadm platformu emülatörde kurulmaz (Floci EC2'yi container olarak açıyor, systemd ve kernel modülü yok). Platformun doğrulaması AWS'de yapılır.
