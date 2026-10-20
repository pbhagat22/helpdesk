# The master password lives in SSM (created by hand). An ephemeral resource reads it during a
# plan/apply only, and the write-only argument password_wo hands it to RDS: neither is ever
# written to the state file, unlike a data source or the plain `password` argument.
ephemeral "aws_ssm_parameter" "db_password" {
  count           = var.running ? 1 : 0
  arn             = "${local.ssm_parameter_prefix}/db-password"
  with_decryption = true
}

# Postgres 18, private, single-AZ, no backups: the database starts empty every session. The
# endpoint depends only on the identifier, so it's the same every time (it's fixed in ecs.tf).
resource "aws_db_instance" "db" {
  count = var.running ? 1 : 0

  identifier        = "helpdesk-db"
  engine            = "postgres"
  engine_version    = "18.6"
  instance_class    = "db.t4g.micro"
  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  db_name             = "helpdesk"
  username            = "helpdesk"
  password_wo         = ephemeral.aws_ssm_parameter.db_password[0].value
  password_wo_version = 1 # write-only values can't be compared; bump this to push a changed password

  db_subnet_group_name   = aws_db_subnet_group.db.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period      = 0
  skip_final_snapshot          = true
  delete_automated_backups     = true
  monitoring_interval          = 0
  performance_insights_enabled = false
  apply_immediately            = true
}
