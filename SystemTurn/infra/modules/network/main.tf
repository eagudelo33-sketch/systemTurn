# VPC with 2 AZs x 3 tiers. See README.md for the NAT / VPC endpoint cost tradeoff.
data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_region" "current" {}

locals {
  azs       = slice(sort(data.aws_availability_zones.available.names), 0, 2)
  nat_count = { none = 0, single = 1, per_az = length(local.azs) }[var.nat_mode]

  # cidrsubnet(var.cidr, 4, n): public 0-1, private_app 2-3, data 4-5.
  subnet_cidrs = {
    public      = [for i in range(2) : cidrsubnet(var.cidr, 4, i)]
    private_app = [for i in range(2) : cidrsubnet(var.cidr, 4, i + 2)]
    data        = [for i in range(2) : cidrsubnet(var.cidr, 4, i + 4)]
  }

  interface_endpoints = var.enable_vpc_endpoints ? toset(["ecr.api", "ecr.dkr", "logs", "secretsmanager"]) : toset([])
}

resource "aws_vpc" "this" {
  cidr_block           = var.cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = var.name }
}

# Lock down the default security group so nothing can accidentally use it.
resource "aws_default_security_group" "this" {
  vpc_id = aws_vpc.this.id

  tags = { Name = "${var.name}-default-locked" }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = { Name = var.name }
}

# --- Subnets ---

resource "aws_subnet" "public" {
  count = length(local.azs)

  vpc_id                  = aws_vpc.this.id
  availability_zone       = local.azs[count.index]
  cidr_block              = local.subnet_cidrs.public[count.index]
  map_public_ip_on_launch = false

  tags = { Name = "${var.name}-public-${local.azs[count.index]}", Tier = "public" }
}

resource "aws_subnet" "private_app" {
  count = length(local.azs)

  vpc_id            = aws_vpc.this.id
  availability_zone = local.azs[count.index]
  cidr_block        = local.subnet_cidrs.private_app[count.index]

  tags = { Name = "${var.name}-private-app-${local.azs[count.index]}", Tier = "private_app" }
}

resource "aws_subnet" "data" {
  count = length(local.azs)

  vpc_id            = aws_vpc.this.id
  availability_zone = local.azs[count.index]
  cidr_block        = local.subnet_cidrs.data[count.index]

  tags = { Name = "${var.name}-data-${local.azs[count.index]}", Tier = "data" }
}

# --- Public tier: one route table, default route to the internet gateway ---

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = { Name = "${var.name}-public" }
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  count = length(local.azs)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# --- NAT (nat_mode): none = 0, single = 1, per_az = 2 ---

resource "aws_eip" "nat" {
  count = local.nat_count

  domain = "vpc"

  tags = { Name = "${var.name}-nat-${local.azs[count.index]}" }

  depends_on = [aws_internet_gateway.this]
}

resource "aws_nat_gateway" "this" {
  count = local.nat_count

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = { Name = "${var.name}-nat-${local.azs[count.index]}" }

  depends_on = [aws_internet_gateway.this]
}

# --- Private app tier: one route table per AZ, default route only when NAT exists ---

resource "aws_route_table" "private_app" {
  count = length(local.azs)

  vpc_id = aws_vpc.this.id

  tags = { Name = "${var.name}-private-app-${local.azs[count.index]}" }
}

resource "aws_route" "private_app_nat" {
  count = local.nat_count > 0 ? length(local.azs) : 0

  route_table_id         = aws_route_table.private_app[count.index].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.this[var.nat_mode == "per_az" ? count.index : 0].id
}

resource "aws_route_table_association" "private_app" {
  count = length(local.azs)

  subnet_id      = aws_subnet.private_app[count.index].id
  route_table_id = aws_route_table.private_app[count.index].id
}

# --- Data tier: no route outside the VPC. `route = []` makes Terraform remove any route added out of band. ---

resource "aws_route_table" "data" {
  vpc_id = aws_vpc.this.id
  route  = []

  tags = { Name = "${var.name}-data" }
}

resource "aws_route_table_association" "data" {
  count = length(local.azs)

  subnet_id      = aws_subnet.data[count.index].id
  route_table_id = aws_route_table.data.id
}

# --- VPC endpoints ---

# Gateway endpoints are free: S3 traffic (including ECR image layers) skips the NAT.
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${data.aws_region.current.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = aws_route_table.private_app[*].id

  tags = { Name = "${var.name}-s3" }
}

resource "aws_security_group" "endpoints" {
  count = var.enable_vpc_endpoints ? 1 : 0

  name        = "${var.name}-vpc-endpoints"
  description = "HTTPS from inside the VPC to interface endpoints"
  vpc_id      = aws_vpc.this.id

  tags = { Name = "${var.name}-vpc-endpoints" }
}

resource "aws_vpc_security_group_ingress_rule" "endpoints_https" {
  count = var.enable_vpc_endpoints ? 1 : 0

  security_group_id = aws_security_group.endpoints[0].id
  description       = "HTTPS from the VPC"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
  cidr_ipv4         = var.cidr
}

resource "aws_vpc_endpoint" "interface" {
  for_each = local.interface_endpoints

  vpc_id              = aws_vpc.this.id
  service_name        = "com.amazonaws.${data.aws_region.current.region}.${each.key}"
  vpc_endpoint_type   = "Interface"
  subnet_ids          = aws_subnet.private_app[*].id
  security_group_ids  = [aws_security_group.endpoints[0].id]
  private_dns_enabled = true

  tags = { Name = "${var.name}-${each.key}" }
}
