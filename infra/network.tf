# Security groups: CloudFront → ALB → app → (RDS, Kafka/Elasticsearch). Each one only accepts
# traffic from the group in front of it. Rules are separate resources (the provider's recommended
# style), not inline blocks, so adding one rule never rewrites the others.

resource "aws_security_group" "alb" {
  name        = "helpdesk-alb"
  description = "helpdesk load balancer"
  vpc_id      = data.aws_vpc.default.id
}

resource "aws_security_group" "app" {
  name        = "helpdesk-app"
  description = "helpdesk app tasks"
  vpc_id      = data.aws_vpc.default.id
}

resource "aws_security_group" "db" {
  name        = "helpdesk-db"
  description = "helpdesk RDS"
  vpc_id      = data.aws_vpc.default.id
}

resource "aws_security_group" "infra" {
  name        = "helpdesk-infra"
  description = "helpdesk kafka and elasticsearch"
  vpc_id      = data.aws_vpc.default.id
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  security_group_id = aws_security_group.alb.id
  description       = "CloudFront only"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
}

resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  security_group_id            = aws_security_group.app.id
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  referenced_security_group_id = aws_security_group.alb.id
}

resource "aws_vpc_security_group_ingress_rule" "db_from_app" {
  security_group_id            = aws_security_group.db.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = aws_security_group.app.id
}

resource "aws_vpc_security_group_ingress_rule" "kafka_from_app" {
  security_group_id            = aws_security_group.infra.id
  ip_protocol                  = "tcp"
  from_port                    = 9092
  to_port                      = 9092
  referenced_security_group_id = aws_security_group.app.id
}

resource "aws_vpc_security_group_ingress_rule" "elasticsearch_from_app" {
  security_group_id            = aws_security_group.infra.id
  ip_protocol                  = "tcp"
  from_port                    = 9200
  to_port                      = 9200
  referenced_security_group_id = aws_security_group.app.id
}

# Outbound: everything (image pulls from ECR and Docker Hub, the ALB reaching tasks, ...).
# for_each makes one rule per group, addressed as aws_vpc_security_group_egress_rule.all["app"] etc.
resource "aws_vpc_security_group_egress_rule" "all" {
  for_each = {
    alb   = aws_security_group.alb.id
    app   = aws_security_group.app.id
    db    = aws_security_group.db.id
    infra = aws_security_group.infra.id
  }
  security_group_id = each.value
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_db_subnet_group" "db" {
  name        = "helpdesk-db"
  description = "helpdesk default VPC subnets"
  subnet_ids  = data.aws_subnets.default.ids
}
