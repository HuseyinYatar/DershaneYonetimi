output "public_ip" {
  description = "Write this into the A records of @ and * at the registrar."
  value       = aws_eip.nlb.public_ip
}

output "dns_name" {
  value = one(aws_lb.this[*].dns_name)
}
