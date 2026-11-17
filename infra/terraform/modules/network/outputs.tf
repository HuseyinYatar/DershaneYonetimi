output "vpc_id" {
  value = aws_vpc.this.id
}

output "public_subnet_id" {
  value = aws_subnet.public.id
}

output "public_subnet_cidr" {
  value = aws_subnet.public.cidr_block
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "nodes_security_group_id" {
  value = aws_security_group.nodes.id
}

output "nlb_security_group_id" {
  value = aws_security_group.nlb.id
}

output "rds_security_group_id" {
  value = aws_security_group.rds.id
}

output "redis_security_group_id" {
  value = aws_security_group.redis.id
}
