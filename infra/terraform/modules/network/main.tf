# One VPC. The nodes and the NLB share a single public subnet (one AZ); RDS and ElastiCache sit in two
# private subnets because their subnet groups need two AZs. There is no NAT gateway (~40 USD a month):
# nodes reach the internet with their public IPs and the security groups below limit what comes in.

data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = var.name }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = var.name }
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, 1)
  availability_zone       = local.azs[0]
  map_public_ip_on_launch = true #bu subnet'te açılan EC2'lere kendiliğinden public IP verilir.

  tags = { Name = "${var.name}-public-a" }
}

resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.this.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, 11 + count.index)
  availability_zone = local.azs[count.index]

  tags = { Name = "${var.name}-private-${substr("ab", count.index, 1)}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${var.name}-public" }
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}
#tabloyu subnet'e bağlar.
resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# No internet route: the databases only talk to the nodes.
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id
  tags   = { Name = "${var.name}-private" }
}

resource "aws_route_table_association" "private" {
  count          = 2
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# --- Security groups ----------------------------------------------------------------------------

resource "aws_security_group" "nodes" {
  name        = "${var.name}-nodes"
  description = "Kubernetes nodes"
  vpc_id      = aws_vpc.this.id
}

resource "aws_security_group" "nlb" {
  name        = "${var.name}-nlb"
  description = "Public network load balancer"
  vpc_id      = aws_vpc.this.id
}

resource "aws_security_group" "rds" {
  name        = "${var.name}-rds"
  description = "PostgreSQL, reachable from the nodes only"
  vpc_id      = aws_vpc.this.id
}

resource "aws_security_group" "redis" {
  name        = "${var.name}-redis"
  description = "Redis, reachable from the nodes only"
  vpc_id      = aws_vpc.this.id
}

# Node to node: kubelet (10250), etcd (2379-2380), API server (6443), CNI encapsulation (VXLAN/IPIP)
# and pod traffic. Allowing the group itself covers all of them, whatever the CNI.
resource "aws_vpc_security_group_ingress_rule" "nodes_self" {
  security_group_id            = aws_security_group.nodes.id
  referenced_security_group_id = aws_security_group.nodes.id
  ip_protocol                  = "-1"
  description                  = "Node to node"
}

resource "aws_vpc_security_group_ingress_rule" "nodes_ssh" {
  for_each          = toset(var.admin_cidrs)
  security_group_id = aws_security_group.nodes.id
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
  description       = "SSH from an admin address"
}

resource "aws_vpc_security_group_ingress_rule" "nodes_api" {
  for_each          = toset(var.admin_cidrs)
  security_group_id = aws_security_group.nodes.id
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 6443
  to_port           = 6443
  description       = "kubectl from an admin address"
}

resource "aws_vpc_security_group_ingress_rule" "nodes_ingress" {
  for_each                     = toset([tostring(var.ingress_http_node_port), tostring(var.ingress_https_node_port)])
  security_group_id            = aws_security_group.nodes.id
  referenced_security_group_id = aws_security_group.nlb.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
  description                  = "Traefik NodePort from the NLB"
}

# Image pulls, package installs, AWS APIs and Let's Encrypt all leave through the internet gateway.
resource "aws_vpc_security_group_egress_rule" "nodes_all" {
  security_group_id = aws_security_group.nodes.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
  description       = "Outbound"
}

resource "aws_vpc_security_group_ingress_rule" "nlb_public" {
  for_each          = toset(["80", "443"])
  security_group_id = aws_security_group.nlb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = tonumber(each.value)
  to_port           = tonumber(each.value)
  description       = "Public HTTP(S)"
}

resource "aws_vpc_security_group_egress_rule" "nlb_to_nodes" {
  for_each                     = toset([tostring(var.ingress_http_node_port), tostring(var.ingress_https_node_port)])
  security_group_id            = aws_security_group.nlb.id
  referenced_security_group_id = aws_security_group.nodes.id
  ip_protocol                  = "tcp"
  from_port                    = tonumber(each.value)
  to_port                      = tonumber(each.value)
  description                  = "Forward and health check to Traefik"
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_nodes" {
  security_group_id            = aws_security_group.rds.id
  referenced_security_group_id = aws_security_group.nodes.id
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  description                  = "PostgreSQL from the nodes"
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_nodes" {
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.nodes.id
  ip_protocol                  = "tcp"
  from_port                    = 6379
  to_port                      = 6379
  description                  = "Redis from the nodes"
}
