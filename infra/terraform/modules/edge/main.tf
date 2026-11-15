# Public entry: a network load balancer with a fixed Elastic IP. The domain's A records at the
# registrar (@ and *) point at this IP, so the NLB can be removed in standby and recreated without a
# DNS change. TLS is not terminated here: 80 and 443 pass as TCP to Traefik's NodePorts, and
# cert-manager inside the cluster obtains the Let's Encrypt certificate.

locals {
  listeners = {
    http  = { port = 80, node_port = var.http_node_port }
    https = { port = 443, node_port = var.https_node_port }
  }

  # (listener, worker) pairs for the target group attachments.
  attachments = {
    for pair in setproduct(keys(local.listeners), range(length(var.target_instance_ids))) :
    "${pair[0]}-${pair[1]}" => { listener = pair[0], instance_id = var.target_instance_ids[pair[1]] }
  }
}

resource "aws_eip" "nlb" {
  domain = "vpc"
  tags   = { Name = "${var.name}-nlb" }
}

resource "aws_lb" "this" {
  count              = var.enabled ? 1 : 0
  name               = var.name
  load_balancer_type = "network"
  internal           = false
  security_groups    = [var.security_group_id]

  subnet_mapping {
    subnet_id     = var.subnet_id
    allocation_id = aws_eip.nlb.id
  }
}

resource "aws_lb_target_group" "this" {
  for_each    = var.enabled ? local.listeners : {}
  name        = "${var.name}-${each.key}"
  protocol    = "TCP"
  port        = each.value.node_port
  target_type = "instance"
  vpc_id      = var.vpc_id

  # Traffic then arrives from the NLB's own address, which the node security group allows by
  # referencing the NLB group. Traefik sees the NLB address; turn on proxy protocol to see clients.
  preserve_client_ip   = false
  deregistration_delay = 30

  health_check {
    protocol            = "TCP"
    port                = "traffic-port"
    interval            = 10
    healthy_threshold   = 3
    unhealthy_threshold = 3
  }
}

resource "aws_lb_target_group_attachment" "this" {
  for_each         = var.enabled ? local.attachments : {}
  target_group_arn = aws_lb_target_group.this[each.value.listener].arn
  target_id        = each.value.instance_id
}

resource "aws_lb_listener" "this" {
  for_each          = var.enabled ? local.listeners : {}
  load_balancer_arn = aws_lb.this[0].arn
  protocol          = "TCP"
  port              = each.value.port

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.this[each.key].arn
  }
}
