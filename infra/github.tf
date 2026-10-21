# Lets GitHub Actions deploy without any AWS keys stored in GitHub.
#
# For each job, GitHub issues a short-lived OIDC token (a signed JWT) saying which repository,
# branch and workflow it is. AWS trusts GitHub's issuer (this provider), checks the token
# against the role's trust policy, and hands back temporary credentials (valid 1 hour).
# Nothing long-lived exists that could leak.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"] # the token's "aud", set by configure-aws-credentials
}

locals {
  github_repository = "pbhagat22/helpdesk"
  # The repository uses GitHub's immutable subject claims: the token's "sub" carries the owner's
  # and repository's numeric IDs next to their names. IDs are never reused, so a new repository
  # created under this name after a rename or delete couldn't assume the role.
  # Check with: gh api repos/pbhagat22/helpdesk/actions/oidc/customization/sub
  github_sub_prefix = "repo:pbhagat22@235375028/helpdesk@1418245817"
}

resource "aws_iam_role" "github_deploy" {
  name        = "helpdesk-github-deploy"
  description = "Assumed by GitHub Actions (main branch of ${local.github_repository}) to push and deploy the app"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          # "sub" names the repository and ref. Only workflow runs on main get in: not other
          # branches, not pull requests (a PR could otherwise change the workflow and deploy),
          # and not any other repository on GitHub.
          "token.actions.githubusercontent.com:sub" = "${local.github_sub_prefix}:ref:refs/heads/main"
        }
      }
    }]
  })
}

# Least privilege: push this one repository, and roll this one service. No Terraform, no RDS,
# no IAM changes. A leaked token could at worst deploy an image.
resource "aws_iam_role_policy" "github_deploy" {
  name = "push-and-deploy"
  role = aws_iam_role.github_deploy.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrLogin"
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken" # account-wide by design; grants nothing by itself
        Resource = "*"
      },
      {
        Sid    = "EcrPush"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:PutImage",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer",
        ]
        Resource = aws_ecr_repository.app.arn
      },
      {
        Sid      = "EcsRollApp"
        Effect   = "Allow"
        Action   = ["ecs:DescribeServices", "ecs:UpdateService"]
        Resource = "arn:aws:ecs:${local.region}:${local.account_id}:service/${aws_ecs_cluster.helpdesk.name}/app"
      },
      {
        Sid      = "EcsTaskDefinitions"
        Effect   = "Allow"
        Action   = ["ecs:DescribeTaskDefinition", "ecs:RegisterTaskDefinition"] # no resource-level permissions exist
        Resource = "*"
      },
      {
        # Registering a task definition that names a role counts as "passing" it
        Sid      = "PassTaskRoles"
        Effect   = "Allow"
        Action   = "iam:PassRole"
        Resource = [aws_iam_role.ecs_execution.arn, aws_iam_role.app_task.arn]
        Condition = {
          StringEquals = { "iam:PassedToService" = "ecs-tasks.amazonaws.com" }
        }
      },
    ]
  })
}

output "github_deploy_role_arn" {
  value = aws_iam_role.github_deploy.arn
}
