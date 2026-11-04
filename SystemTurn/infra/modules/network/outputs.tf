output "vpc_id" {
  description = "VPC id."
  value       = aws_vpc.this.id
}

output "vpc_cidr" {
  description = "VPC IPv4 CIDR."
  value       = aws_vpc.this.cidr_block
}

output "azs" {
  description = "The two availability zones used, in subnet order."
  value       = local.azs
}

output "public_subnet_ids" {
  description = "Public subnet ids (ALB, NAT), one per AZ."
  value       = aws_subnet.public[*].id
}

output "private_app_subnet_ids" {
  description = "Private app subnet ids (ECS tasks), one per AZ."
  value       = aws_subnet.private_app[*].id
}

output "data_subnet_ids" {
  description = "Data subnet ids (RDS), one per AZ. No route outside the VPC."
  value       = aws_subnet.data[*].id
}

output "public_route_table_id" {
  description = "Public route table id."
  value       = aws_route_table.public.id
}

output "private_app_route_table_ids" {
  description = "Private app route table ids, one per AZ."
  value       = aws_route_table.private_app[*].id
}

output "data_route_table_id" {
  description = "Data route table id."
  value       = aws_route_table.data.id
}

output "nat_gateway_ids" {
  description = "NAT gateway ids (empty when nat_mode = none)."
  value       = aws_nat_gateway.this[*].id
}

output "internet_egress_enabled" {
  description = "True when private_app subnets can reach AWS APIs (NAT or interface endpoints)."
  value       = var.nat_mode != "none" || var.enable_vpc_endpoints
}
