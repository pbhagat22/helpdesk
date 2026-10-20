# Private DNS (Cloud Map) so the app finds Kafka and Elasticsearch by name:
# kafka.helpdesk.local and elasticsearch.helpdesk.local. ECS registers each task's IP there.
resource "aws_service_discovery_private_dns_namespace" "helpdesk" {
  count = var.running ? 1 : 0
  name  = "helpdesk.local"
  vpc   = data.aws_vpc.default.id
}

locals {
  # Kafka and Elasticsearch are set up the same way, so one for_each covers both;
  # addresses look like aws_ecs_service.infra["kafka"]. An empty set when not running.
  infra_services = var.running ? {
    kafka         = aws_ecs_task_definition.kafka.arn
    elasticsearch = aws_ecs_task_definition.elasticsearch.arn
  } : {}
}

resource "aws_service_discovery_service" "infra" {
  for_each = local.infra_services
  name     = each.key

  dns_config {
    namespace_id   = aws_service_discovery_private_dns_namespace.helpdesk[0].id
    routing_policy = "MULTIVALUE"
    dns_records {
      type = "A"
      ttl  = 10
    }
  }
  health_check_custom_config {} # ECS reports task health to Cloud Map

  force_destroy = true # deregister leftover task IPs on destroy instead of failing
}

resource "aws_ecs_service" "infra" {
  for_each        = local.infra_services
  name            = each.key
  cluster         = aws_ecs_cluster.helpdesk.id
  task_definition = each.value
  desired_count   = 1
  launch_type     = "FARGATE"

  # Public IPs because there's no NAT gateway: tasks pull images straight from the internet
  network_configuration {
    subnets          = data.aws_subnets.tasks.ids
    security_groups  = [aws_security_group.infra.id]
    assign_public_ip = true
  }

  service_registries {
    registry_arn = aws_service_discovery_service.infra[each.key].arn
  }
}

resource "aws_ecs_service" "app" {
  count           = var.running ? 1 : 0
  name            = "app"
  cluster         = aws_ecs_cluster.helpdesk.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = data.aws_subnets.tasks.ids
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app[0].arn
    container_name   = "app"
    container_port   = 8080
  }
  # Spring Boot + Flyway take a while on 0.5 vCPU; don't let failed health checks kill it meanwhile
  health_check_grace_period_seconds = 300

  # Terraform orders creation by references. Nothing in this block refers to these, so say it
  # explicitly: the app's first start needs the database available (aws_db_instance only finishes
  # creating once it is) and Kafka/Elasticsearch on their way, and the target group needs its
  # listener before ECS can attach to it.
  depends_on = [aws_db_instance.db, aws_ecs_service.infra, aws_lb_listener.http]
}
