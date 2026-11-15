# AWS, applied once by hand with an administrator SSO session:
#   export TF_VAR_alert_email='<address>'
#   terraform -chdir=infra/terraform/bootstrap init
#   terraform -chdir=infra/terraform/bootstrap apply -var-file=environments/prod.tfvars
budget_limit_usd = 50
github_repo      = "HuseyinYatar/DershaneYonetimi"
