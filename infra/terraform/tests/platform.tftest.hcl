# terraform test: runs the configuration with mocked providers, so it needs no AWS account, no Floci
# and no credentials. It checks the wiring (counts, for_each keys, IRSA vs node roles, standby), not
# AWS behaviour. Topics and subscriptions come from events.auto.tfvars.
#   terraform init -backend=false && terraform test

mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = { account_id = "123456789012" }
  }

  mock_data "aws_region" {
    defaults = { region = "eu-central-1" }
  }

  mock_data "aws_availability_zones" {
    defaults = { names = ["eu-central-1a", "eu-central-1b", "eu-central-1c"] }
  }

  mock_data "aws_ssm_parameter" {
    defaults = { insecure_value = "ami-0123456789abcdef0" }
  }

  mock_data "aws_iam_openid_connect_provider" {
    defaults = { arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com" }
  }

  mock_resource "aws_db_instance" {
    defaults = {
      master_user_secret = [{
        secret_arn    = "arn:aws:secretsmanager:eu-central-1:123456789012:secret:rds!db-1234"
        kms_key_id    = "alias/aws/secretsmanager"
        secret_status = "active"
      }]
    }
  }

  # The provider validates ARN arguments; mocked ARNs must look real where they are passed on.
  mock_resource "aws_sns_topic" {
    defaults = { arn = "arn:aws:sns:eu-central-1:123456789012:mock-topic" }
  }

  mock_resource "aws_sqs_queue" {
    defaults = { arn = "arn:aws:sqs:eu-central-1:123456789012:mock-queue" }
  }

  mock_resource "aws_iam_policy" {
    defaults = { arn = "arn:aws:iam::123456789012:policy/mock" }
  }

  mock_resource "aws_lb" {
    defaults = { arn = "arn:aws:elasticloadbalancing:eu-central-1:123456789012:loadbalancer/net/mock/0123456789abcdef" }
  }

  mock_resource "aws_lb_target_group" {
    defaults = { arn = "arn:aws:elasticloadbalancing:eu-central-1:123456789012:targetgroup/mock/0123456789abcdef" }
  }
}

# The real random provider: it runs locally and its passwords satisfy the Redis AUTH rules.

variables {
  environment   = "prod"
  bucket_prefix = "dershane-prod-"
  buckets       = { "exam-analysis-optical-files" = {}, "file-uploads" = {} }
  domain        = "onlinecourses.site"
  github_repo   = "HuseyinYatar/DershaneYonetimi"
}

run "local_has_events_and_storage_only" {
  command = plan

  variables {
    environment   = "local"
    bucket_prefix = ""
    domain        = null
  }

  assert {
    condition     = output.control_plane_public_ip == null && output.redis_address == null
    error_message = "platform_enabled = false must not create platform resources"
  }

  assert {
    condition     = module.storage.bucket_names["exam-analysis-optical-files"] == "exam-analysis-optical-files"
    error_message = "Without a prefix the bucket keeps the name the services default to"
  }
}

run "prod_with_irsa" {
  command = apply

  variables {
    platform_enabled = true
    alert_email      = "alerts@example.com"
  }

  assert {
    condition     = keys(output.nodes) == ["cp", "w1", "w2"]
    error_message = "Expected one control plane and two workers"
  }

  assert {
    condition     = output.control_plane_private_ip == "10.0.1.10" && output.nodes["w2"].private_ip == "10.0.1.12"
    error_message = "Nodes must keep their fixed private IPs"
  }

  assert {
    condition = toset(keys(output.service_role_arns)) == toset([
      "user", "attendance", "task", "messaging", "exam-analysis", "notification", "file", "external-secrets",
    ])
    error_message = "IRSA roles: one per service that calls AWS, plus External Secrets; none for api-gateway or keycloak"
  }

  assert {
    condition     = startswith(output.oidc_issuer, "https://dershane-prod-oidc-123456789012.s3.eu-central-1.amazonaws.com")
    error_message = "Unexpected OIDC issuer"
  }

  assert {
    condition     = output.redis_address != null && output.ingress_public_ip != null
    error_message = "Cache and NLB must exist outside standby"
  }

  assert {
    condition     = output.ses_dkim_records != null && output.smtp_host == "email-smtp.eu-central-1.amazonaws.com"
    error_message = "A domain enables SES and its SMTP secret"
  }

  assert {
    condition     = length(output.db_secret_arns) == 8
    error_message = "One secret per service database"
  }

  assert {
    condition     = output.bucket_names["etcd-backups"] == "dershane-prod-etcd-backups"
    error_message = "The platform adds the etcd backup bucket"
  }
}

run "standby_with_node_role" {
  command = apply

  variables {
    platform_enabled = true
    standby          = true
    pod_identity     = "node"
  }

  assert {
    condition     = output.redis_address == null
    error_message = "standby removes ElastiCache"
  }

  assert {
    condition     = output.ingress_public_ip != null
    error_message = "standby keeps the NLB Elastic IP so DNS stays valid"
  }

  assert {
    condition     = length(output.service_role_arns) == 0 && output.oidc_issuer == null
    error_message = "pod_identity = node creates no IRSA roles"
  }
}
