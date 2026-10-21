# The role ECS itself uses to start tasks: pull the image from ECR, write logs, and read the
# SSM secrets into the container's environment. The app code itself runs as aws_iam_role.app_task below.
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

# The role the app's CODE runs as (the "task role"), unlike the execution role above, which ECS
# uses to start the task. The AWS SDK in the container picks its credentials up automatically
# (DefaultCredentialsProvider), so no keys are stored anywhere.
resource "aws_iam_role" "app_task" {
  name = "helpdesk-app-task"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# The AI features call Bedrock's Converse API, which needs bedrock:InvokeModel, and only for the
# models the app is configured with (local.ai_model and local.ai_agent_model in ecs.tf).
resource "aws_iam_role_policy" "app_invoke_model" {
  name = "invoke-ai-model"
  role = aws_iam_role.app_task.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "bedrock:InvokeModel"
      Resource = [for model in [local.ai_model, local.ai_agent_model] : "arn:aws:bedrock:${local.region}::foundation-model/${model}"]
    }]
  })
}
