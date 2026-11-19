# Ansible: kubeadm kümesinin kurulumu

Terraform'un açtığı üç EC2 sunucusunu (`cp`, `w1`, `w2`) Kubernetes kümesine dönüştürür ve kümeyi Argo CD'ye devreder. Laptop'tan SSH ile çalışır; sunuculara ajan kurulmaz.

| Araç | Ne yapar |
|---|---|
| Terraform (`infra/terraform`) | AWS kaynaklarını açar: ağ, sunucular, IAM, RDS... |
| Ansible (bu klasör) | Sunucuların içini yapılandırır ve kümeyi kurar |
| Argo CD (`k8s/`) | Kümenin içinde çalışacak her şeyi Git'ten uygular |

## Gerekenler

- Ansible: `brew install ansible` (ansible-core ve koleksiyonlar birlikte gelir) veya `pipx install ansible-core`, ardından `ansible-galaxy collection install -r requirements.yml`.
- `jq` (macOS'ta yüklü gelir).
- AWS oturumu (`aws login`) ve `infra/terraform`'un prod backend'iyle `init` edilmiş olması. Betikler `terraform output` okur.
- Terraform'a verilen SSH anahtarının laptop'ta olması (`~/.ssh/id_ed25519`). Farklı bir dosyaysa `ANSIBLE_PRIVATE_KEY_FILE=...`.
- Laptop'un IP'sinin `TF_VAR_admin_cidrs` içinde olması: 22 (SSH) ve 6443 (kubectl) yalnızca o adreslere açık.

## Çalıştırma

```bash
cd infra/ansible
./inventory.sh          # inventory.ini: cp, w1, w2 ve IP'leri (her oturumda; worker IP'leri değişir)
./tf-vars.sh            # group_vars/all/terraform.json: OIDC, bucket ve cp IP değerleri
ansible-playbook site.yml

export KUBECONFIG=~/.kube/dershane-prod.config
kubectl get nodes       # cp, w1, w2 Ready
```

Tek bir adımı çalıştırmak için etiket kullanılır: `ansible-playbook site.yml --tags node_prep`.

Önce ne yapılacağını görmek için `--check --diff` kullanılır. Bu modda komut çalıştıran adımlar (`kubeadm`, `kubectl`) atlanır; yalnızca dosya değişiklikleri gösterilir.

Playbook tekrar çalıştırılabilir. Her adım önce mevcut duruma bakar, yalnızca farklı olanı değiştirir. Kurulmuş bir kümede ikinci çalıştırma `changed=0` ile biter.

## Adımlar (`site.yml`)

| # | Etiket | Nerede | Ne yapar |
|---|---|---|---|
| 1 | `node_prep` | tüm node'lar | hostname, swap kapatma, kernel modülleri ve sysctl, containerd (systemd cgroup), kubelet/kubeadm/kubectl (hold), ecr-credential-provider, cp'ye AWS CLI |
| 2 | `control_plane` | cp | Secret şifreleme anahtarı (Secrets Manager'dan geri alınır, yoksa üretilip oraya yazılır) ve `EncryptionConfiguration`, `kubeadm init` (yalnızca ilk sefer), `keys.json`'ın OIDC bucket'ına yüklenmesi (IRSA), admin kubeconfig'in Secrets Manager'a yazılması |
| 3 | `workers` | w1, w2 | Henüz katılmamış worker için join komutu üretilir ve `kubeadm join` çalışır |
| 4 | `cni` | cp | Calico (Tigera operator, `192.168.0.0/16`, VXLANCrossSubnet); bütün node'lar Ready olana kadar bekler |
| 5 | `etcd_backup` | cp | Günlük etcd snapshot'ı S3'e (systemd timer; ayrıca her açılıştan 10 dk sonra) |
| 6 | `argocd` | cp | Helm, Argo CD ve root Application (`k8s/apps`'i izler) |
| 7 | `kubeconfig` | laptop | `~/.kube/dershane-prod.config`, adresi cp'nin Elastic IP'si |

Sürümler tek yerde: `group_vars/all/main.yml`.

## Klasör yapısı

```
infra/ansible/
├── ansible.cfg             inventory yolu, ssh ayarları
├── requirements.yml        gereken koleksiyonlar (ansible.posix, community.general)
├── inventory.sh            terraform output → inventory.ini
├── tf-vars.sh              terraform output → group_vars/all/terraform.json
├── site.yml                adımların sırası
├── group_vars/all/main.yml sürümler, ağ aralıkları, repo adresi
└── roles/<rol>/
    ├── tasks/main.yml      adımlar
    ├── defaults/main.yml   rolün kendi değişkenleri
    ├── handlers/main.yml   yalnızca bir şey değişince çalışan adımlar (yeniden başlatma)
    └── templates/*.j2      değişkenle doldurulan dosyalar
```

`inventory.ini`, `group_vars/all/terraform.json` ve `.ssh_known_hosts` üretilir ve Git'e girmez.

## Sık durumlar

- **Sunucular durdurulup açıldı (`power.sh`):** Küme kendiliğinden geri gelir; private IP'ler değişmez. Ansible'ı yeniden çalıştırmak gerekmez. Yalnızca SSH için `./inventory.sh` yeniden çalıştırılır.
- **Bir worker silinip yeniden açıldı:** `./inventory.sh && ansible-playbook site.yml`. Yalnızca yeni worker hazırlanır ve kümeye katılır. Eski node kümeden elle silinir: `kubectl delete node w1`.
- **Argo CD arayüzü:** `kubectl -n argocd port-forward svc/argocd-server 8080:443`, ardından https://localhost:8080. Kullanıcı `admin`, parola `kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d`.
- **etcd yedeği elle:** cp'de `sudo systemctl start etcd-backup.service`, sonuç için `journalctl -u etcd-backup.service`. Yedekler `s3://<etcd bucket>/cp/` altında 14 gün kalır.
- **etcd'yi yedekten geri yükleme** (elle; küme durumu snapshot anına döner): snapshot S3'ten indirilir, `etcdutl snapshot restore` ile yeni bir veri dizinine açılır, `/etc/kubernetes/manifests/etcd.yaml` bu dizini gösterecek şekilde değiştirilir. Ayrıntı: Kubernetes belgesi "Operating etcd clusters for Kubernetes", "Restoring an etcd cluster". Yedekteki Secret'lar şifrelidir; API server'ın aynı anahtarı kullanması gerekir. Yeniden kurulan cp'de `control_plane` rolü anahtarı `dershane/cluster/etcd-encryption-key`'den geri alır. Anahtar kaybolursa yedekteki Secret'lar okunamaz; External Secrets onları Secrets Manager'dan yeniden yazar.
- **Secret'ların şifreli olduğunu görmek:** cp'de `sudo crictl exec $(sudo crictl ps --name etcd -q) etcdctl --cacert /etc/kubernetes/pki/etcd/ca.crt --cert /etc/kubernetes/pki/etcd/server.crt --key /etc/kubernetes/pki/etcd/server.key get /registry/secrets/dershane/user-secrets | head -c 80`. Çıktı `k8s:enc:secretbox:v1:key1:` ile başlıyorsa Secret etcd'de şifreli.

## Bilinen sınırlar

- Tek control plane: cp kapanırsa kümeye değişiklik yapılamaz (çalışan pod'lar çalışmaya devam eder).
- `kubeadm-config.yaml` yalnızca ilk `kubeadm init`'te okunur. Sonradan değiştirilirse (ör. yeni bir sertifika adresi) çalışan kümeye kendiliğinden uygulanmaz.
- Kubernetes sürüm yükseltmesi (`kubeadm upgrade`) bu playbook'ta yok.
- Host anahtarları bir oturum boyunca doğrulanır (`accept-new`); `inventory.sh` her oturumda listeyi sıfırlar, çünkü worker IP'leri değişir.
