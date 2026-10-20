# Container registry for the app image. Tags are immutable: each one is a git short sha.
resource "aws_ecr_repository" "app" {
  name                 = "helpdesk"
  image_tag_mutability = "IMMUTABLE"

  # Scanning is switched on by the registry-wide rule below; this per-repository flag isn't honoured.
  image_scanning_configuration {
    scan_on_push = false
  }

  # The pushed images are the only copy, so a stray `terraform destroy` must not delete them.
  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "keep the last 5 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}

# Registry-level (whole account and region): BASIC vulnerability scan of every pushed image
resource "aws_ecr_registry_scanning_configuration" "this" {
  scan_type = "BASIC"
  rule {
    scan_frequency = "SCAN_ON_PUSH"
    repository_filter {
      filter      = "*"
      filter_type = "WILDCARD"
    }
  }
}
