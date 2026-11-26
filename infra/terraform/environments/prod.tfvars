# AWS, eu-central-1, free plan account. Applied by .github/workflows/terraform.yml after approval,
# or by hand: terraform init -backend-config=environments/prod.s3.tfbackend
#             terraform plan -var-file=environments/prod.tfvars
#
# Personal values are not committed. Set them as environment variables (locally) or as repository
# variables (CI, see the workflow):
#   TF_VAR_admin_cidrs='["<your public IP>/32"]'   SSH and kubectl; empty means SSM Session Manager only
#   TF_VAR_ssh_public_key="$(cat ~/.ssh/id_ed25519.pub)"
#   TF_VAR_alert_email='<address>'
#   TF_VAR_ses_test_recipients='["<address>", ...]'
environment   = "prod"
bucket_prefix = "dershane-prod-"

platform_enabled = true
# true removes the NLB and ElastiCache during long breaks (the Elastic IPs and DNS stay).
standby = false

cp_instance_type     = "c7i-flex.large"
worker_instance_type = "m7i-flex.large"
worker_count         = 2

pod_identity = "irsa"
github_repo  = "HuseyinYatar/DershaneYonetimi"
# The repository issues OIDC tokens in GitHub's immutable format (owner and repository ids are public):
#   gh api repos/HuseyinYatar/DershaneYonetimi/actions/oidc/customization/sub
github_oidc_subject_prefix = "repo:HuseyinYatar@168910744/DershaneYonetimi@1420776653"

buckets = {
  "exam-analysis-optical-files" = {}
}

domain = "onlinecourses.site"
