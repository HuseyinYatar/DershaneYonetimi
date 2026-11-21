# tflint --init && tflint --recursive --config "$PWD/.tflint.hcl"   (from infra/terraform)
config {
  call_module_type = "local"
}

plugin "terraform" {
  enabled = true
  preset  = "recommended"
}

# Checks values the AWS API would reject only at apply time (instance types, engine versions...).
plugin "aws" {
  enabled = true
  version = "0.50.0"
  source  = "github.com/terraform-linters/tflint-ruleset-aws"
}
