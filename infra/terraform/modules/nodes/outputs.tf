output "control_plane_public_ip" {
  value = aws_eip.control_plane.public_ip
}

output "control_plane_private_ip" {
  value = aws_instance.this[var.control_plane_key].private_ip
}

output "worker_instance_ids" {
  value = [for key, node in aws_instance.this : node.id if var.nodes[key].role == "worker"]
}

output "nodes" {
  description = "Worker public IPs change on every stop/start; the control plane keeps its Elastic IP."
  value = {
    for key, node in aws_instance.this : key => {
      id         = node.id
      role       = var.nodes[key].role
      private_ip = node.private_ip
      public_ip  = key == var.control_plane_key ? aws_eip.control_plane.public_ip : node.public_ip
    }
  }
}

output "cluster_secret_arns" {
  value = { for key, secret in aws_secretsmanager_secret.cluster : key => secret.arn }
}

output "ansible_inventory" {
  value = join("\n", concat(
    ["[control_plane]"],
    [for key, node in aws_instance.this : "${key} ansible_host=${aws_eip.control_plane.public_ip} private_ip=${node.private_ip}" if key == var.control_plane_key],
    ["", "[workers]"],
    [for key, node in aws_instance.this : "${key} ansible_host=${node.public_ip} private_ip=${node.private_ip}" if key != var.control_plane_key],
    ["", "[all:vars]", "ansible_user=ubuntu", ""],
  ))
}
