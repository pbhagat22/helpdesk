# Application load balancer: CloudFront → ALB (HTTP :80) → app tasks (:8080).
# Its security group only lets CloudFront in (network.tf).
resource "aws_lb" "app" {
  count              = var.running ? 1 : 0
  name               = "helpdesk"
  load_balancer_type = "application"
  internal           = false
  subnets            = data.aws_subnets.tasks.ids
  security_groups    = [aws_security_group.alb.id]
}

resource "aws_lb_target_group" "app" {
  count       = var.running ? 1 : 0
  name        = "helpdesk-app"
  protocol    = "HTTP"
  port        = 8080
  vpc_id      = data.aws_vpc.default.id
  target_type = "ip" # Fargate tasks register by IP address

  health_check {
    path              = "/actuator/health"
    interval          = 15
    healthy_threshold = 2
  }
  deregistration_delay = 30 # seconds to drain a stopping task (default 300)
}

resource "aws_lb_listener" "http" {
  count             = var.running ? 1 : 0
  load_balancer_arn = aws_lb.app[0].arn
  protocol          = "HTTP"
  port              = 80

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app[0].arn
  }
}
