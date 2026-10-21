resource "aws_ecs_cluster" "helpdesk" {
  name = "helpdesk"
  setting {
    name  = "containerInsights"
    value = "disabled" # extra CloudWatch metrics cost money
  }
}

resource "aws_cloudwatch_log_group" "ecs" {
  name              = "/ecs/helpdesk"
  retention_in_days = 1
}

# Task definitions are the ECS equivalent of the services in compose.yaml. They are immutable:
# any change registers a new revision (Terraform shows it as "must be replaced") and deregisters
# the old one. Registered revisions cost nothing; only running tasks do.

# The newest image in ECR, i.e. the last one the deploy workflow pushed from main. Bringing the
# app up therefore always runs the latest build. While it runs, the deploy workflow rolls new
# images out itself (see aws_ecs_service.app); the next apply then shows this task definition
# being replaced with the new tag, which only re-registers what's already deployed.
data "aws_ecr_image" "app" {
  repository_name = aws_ecr_repository.app.name
  most_recent     = true
}

locals {
  # The Bedrock model behind the AI assistant. One place, because the task role's IAM policy
  # (iam.tf) only allows this model.
  ai_model = "qwen.qwen3-32b-v1:0"

  # Every container logs to the same group, told apart by stream prefix
  log_configuration = {
    for name in ["app", "kafka", "elasticsearch"] : name => {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.ecs.name
        awslogs-region        = local.region
        awslogs-stream-prefix = name
      }
    }
  }
  # ECS wants environment variables as a list of {name, value}; a map is easier to read and write
  env = { for name, vars in {
    app = {
      # The RDS endpoint is the same every time the instance is recreated
      DB_URL                  = "jdbc:postgresql://helpdesk-db.cpow8k2qs3rt.us-east-2.rds.amazonaws.com:5432/helpdesk"
      DB_USERNAME             = "helpdesk"
      KAFKA_BOOTSTRAP_SERVERS = "kafka.helpdesk.local:9092"
      ELASTICSEARCH_URIS      = "http://elasticsearch.helpdesk.local:9200"
      HELPDESK_ADMIN_EMAIL    = "admin@example.com"
      HELPDESK_AI_MODEL       = local.ai_model
    }
    kafka = {
      KAFKA_NODE_ID                                  = "1"
      KAFKA_PROCESS_ROLES                            = "broker,controller"
      KAFKA_CONTROLLER_QUORUM_VOTERS                 = "1@localhost:9093"
      KAFKA_CONTROLLER_LISTENER_NAMES                = "CONTROLLER"
      KAFKA_LISTENERS                                = "PLAINTEXT://:9092,CONTROLLER://:9093"
      KAFKA_ADVERTISED_LISTENERS                     = "PLAINTEXT://kafka.helpdesk.local:9092"
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP           = "PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT"
      KAFKA_INTER_BROKER_LISTENER_NAME               = "PLAINTEXT"
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR         = "1"
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR = "1"
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR            = "1"
      KAFKA_HEAP_OPTS                                = "-Xms512m -Xmx512m"
    }
    elasticsearch = {
      "discovery.type"         = "single-node"
      "xpack.security.enabled" = "false"
      "node.store.allow_mmap"  = "false"
      ES_JAVA_OPTS             = "-Xms512m -Xmx512m"
    }
  } : name => [for k, v in vars : { name = k, value = v }] }
}

resource "aws_ecs_task_definition" "app" {
  family                   = "helpdesk-app"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "2048"
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.app_task.arn
  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }
  container_definitions = jsonencode([{
    name         = "app"
    image        = "${aws_ecr_repository.app.repository_url}:${data.aws_ecr_image.app.image_tags[0]}"
    essential    = true
    portMappings = [{ containerPort = 8080, protocol = "tcp" }]
    environment  = local.env.app
    secrets = [for env_name, parameter in {
      DB_PASSWORD             = "db-password"
      HELPDESK_JWT_SECRET     = "jwt-secret"
      HELPDESK_ADMIN_PASSWORD = "admin-password"
    } : { name = env_name, valueFrom = "${local.ssm_parameter_prefix}/${parameter}" }]
    logConfiguration = local.log_configuration.app
  }])
}

resource "aws_ecs_task_definition" "kafka" {
  family                   = "helpdesk-kafka"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "1024"
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }
  container_definitions = jsonencode([{
    name             = "kafka"
    image            = "apache/kafka:4.2.1"
    essential        = true
    portMappings     = [{ containerPort = 9092, protocol = "tcp" }]
    environment      = local.env.kafka
    logConfiguration = local.log_configuration.kafka
  }])
}

resource "aws_ecs_task_definition" "elasticsearch" {
  family                   = "helpdesk-elasticsearch"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "512"
  memory                   = "2048"
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  runtime_platform {
    cpu_architecture        = "ARM64"
    operating_system_family = "LINUX"
  }
  container_definitions = jsonencode([{
    name             = "elasticsearch"
    image            = "docker.elastic.co/elasticsearch/elasticsearch:9.4.5"
    essential        = true
    portMappings     = [{ containerPort = 9200, protocol = "tcp" }]
    environment      = local.env.elasticsearch
    logConfiguration = local.log_configuration.elasticsearch
  }])
}
