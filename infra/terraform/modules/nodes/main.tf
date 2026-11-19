# EC2 instances for the self-managed kubeadm cluster: one control plane and the workers, all in the
# public subnet with fixed private IPs. They start as plain Ubuntu: preparing the nodes and creating the
# cluster (containerd, kubeadm init/join, Calico, Argo CD) is done by Ansible (infra/ansible).

# Latest Ubuntu 24.04 image, published by Canonical in every region.
data "aws_ssm_parameter" "ubuntu" {
  name = "/aws/service/canonical/ubuntu/server/24.04/stable/current/amd64/hvm/ebs-gp3/ami-id"
}

resource "aws_key_pair" "this" {
  count      = var.ssh_public_key == null ? 0 : 1
  key_name   = var.name
  public_key = var.ssh_public_key
}

resource "aws_instance" "this" {
  for_each = var.nodes

  ami                    = data.aws_ssm_parameter.ubuntu.insecure_value
  instance_type          = each.value.instance_type
  subnet_id              = var.subnet_id
  private_ip             = each.value.private_ip
  vpc_security_group_ids = [var.security_group_id]
  iam_instance_profile   = each.value.role == "control-plane" ? var.control_plane_instance_profile : var.worker_instance_profile
  key_name               = one(aws_key_pair.this[*].key_name)

  # Lets pod traffic leave a node with a pod IP as source (Calico or Cilium without encapsulation).
  source_dest_check = false

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
    # 1 keeps containers away from the node role (IRSA); 2 lets pods use it (pod_identity = "node").
    http_put_response_hop_limit = var.imds_hop_limit
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.volume_size
    encrypted             = true
    delete_on_termination = true
  }

  tags = {
    Name = "${var.name}-${each.key}"
    Role = each.value.role
  }

  # A new Ubuntu image must not replace a running node: replacing the control plane deletes the cluster.
  lifecycle {
    ignore_changes = [ami]
  }
}

# Stable public address for kubectl and the API server certificate (--apiserver-cert-extra-sans).
resource "aws_eip" "control_plane" {
  domain   = "vpc"
  instance = aws_instance.this[var.control_plane_key].id
  tags     = { Name = "${var.name}-${var.control_plane_key}" }
}

# Filled by the control plane (infra/ansible); neither ever goes into Git.
#   kubeconfig             admin kubeconfig, written after kubeadm init
#   etcd-encryption-key    key kube-apiserver encrypts Secrets with in etcd; written before kubeadm init.
#                          An etcd backup restored on a rebuilt control plane needs the same key.
# Join commands are not stored: Ansible creates a one-hour token on the control plane when a worker joins.
resource "aws_secretsmanager_secret" "cluster" {
  for_each                = toset(["kubeconfig", "etcd-encryption-key"])
  name                    = "${var.secret_prefix}/cluster/${each.key}"
  recovery_window_in_days = 0
}
