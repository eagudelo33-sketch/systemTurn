# RDS owns the master password (manage_master_user_password): it creates and rotates a Secrets
# Manager secret {"username","password"}; the ECS task reads its `password` key. Nothing secret
# ever reaches Terraform state or variables. PostgreSQL 15+ defaults rds.force_ssl = 1.
resource "aws_db_subnet_group" "this" {
  name        = "${var.name}-data"
  description = "Data tier subnets (no internet route)"
  subnet_ids  = var.data_subnet_ids
}

resource "aws_db_instance" "this" {
  identifier     = "${var.name}-db"
  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  db_name                     = var.db_name
  username                    = var.db_username
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  backup_retention_period    = var.db_backup_retention_days
  auto_minor_version_upgrade = true
  copy_tags_to_snapshot      = true
  deletion_protection        = var.db_deletion_protection
  skip_final_snapshot        = var.db_skip_final_snapshot
  final_snapshot_identifier  = var.db_skip_final_snapshot ? null : "${var.name}-final"
}
