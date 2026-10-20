# The role ECS itself uses to start tasks: pull the image from ECR, write logs, and read the
# SSM secrets into the container's environment. (The app code has no AWS role; it calls no AWS APIs.)
resource "aws_iam_role" "ecs_execution" {
  name = "helpdesk-ecs-execution"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# The secrets are SSM SecureStrings created by hand and deliberately NOT managed by Terraform:
# a managed parameter's value would be stored in plain text in the state file.
locals {
  ssm_parameter_prefix = "arn:aws:ssm:${local.region}:${local.account_id}:parameter/helpdesk"
}

resource "aws_iam_role_policy" "read_helpdesk_parameters" {
  name = "read-helpdesk-parameters"
  role = aws_iam_role.ecs_execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "ssm:GetParameters"
      Resource = "${local.ssm_parameter_prefix}/*"
    }]
  })
}
