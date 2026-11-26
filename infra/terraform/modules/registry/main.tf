# One ECR repository per image. Tags are commit SHAs and immutable (no latest), images are scanned on
# push, and old images expire. CI pushes from main through GitHub OIDC; no access keys in GitHub.

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

resource "aws_ecr_repository" "this" {
  for_each             = var.repositories
  name                 = each.value
  image_tag_mutability = "IMMUTABLE"
  force_delete         = var.force_delete

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }
}

resource "aws_ecr_lifecycle_policy" "this" {
  for_each   = aws_ecr_repository.this
  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Untagged layers left by failed or replaced pushes"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "Keep the latest images for rollbacks"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = var.keep_images
        }
        action = { type = "expire" }
      },
    ]
  })
}

# --- CI push role (GitHub Actions OIDC; the provider itself is created by bootstrap/) ------------

data "aws_iam_openid_connect_provider" "github" {
  count = var.github_repo == null ? 0 : 1
  url   = "https://token.actions.githubusercontent.com"
}

resource "aws_iam_role" "github_push" {
  count = var.github_repo == null ? 0 : 1
  name  = "${var.name}-github-ecr-push"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = data.aws_iam_openid_connect_provider.github[0].arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          # Exact match. With GitHub's immutable subject format the claim also carries the owner and
          # repository ids, so a renamed or re-created account with the same name cannot take the role.
          "token.actions.githubusercontent.com:sub" = "${coalesce(var.github_oidc_subject_prefix, "repo:${var.github_repo}")}:ref:refs/heads/${var.github_branch}"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "github_push" {
  count = var.github_repo == null ? 0 : 1
  name  = "ecr-push"
  role  = aws_iam_role.github_push[0].name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "Login"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Sid    = "Push"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:CompleteLayerUpload",
          "ecr:DescribeImages",
          "ecr:GetDownloadUrlForLayer",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
        ]
        Resource = [for repo in aws_ecr_repository.this : repo.arn]
      },
    ]
  })
}
