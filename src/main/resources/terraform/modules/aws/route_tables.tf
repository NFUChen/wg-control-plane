# Create single route table for WireGuard traffic
resource "aws_route_table" "wireguard" {
  vpc_id = data.aws_vpc.this.id

  tags = merge(
    {
      Name    = var.route_table_name
      Purpose = "WireGuard routing"
    },
    var.tags
  )
}

# Create routes pointing to WireGuard ENI for each destination CIDR
resource "aws_route" "to_wireguard" {
  for_each = toset(var.destination_cidrs)

  route_table_id         = aws_route_table.wireguard.id
  destination_cidr_block = each.value
  network_interface_id   = var.wireguard_eni_id
}

# Associate all subnets in the VPC with the route table
resource "aws_route_table_association" "auto_associate" {
  for_each = toset(data.aws_subnets.all_subnets.ids)

  route_table_id = aws_route_table.wireguard.id
  subnet_id      = each.value
}