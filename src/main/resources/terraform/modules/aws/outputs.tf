# VPC information
output "vpc_id" {
  description = "VPC ID where WireGuard is deployed"
  value       = data.aws_vpc.this.id
}

output "vpc_cidr" {
  description = "CIDR block of the VPC"
  value       = data.aws_vpc.this.cidr_block
}

# WireGuard ENI information
output "wireguard_eni_id" {
  description = "WireGuard ENI ID"
  value       = var.wireguard_eni_id
}

# Route table
output "route_table_id" {
  description = "WireGuard route table ID"
  value       = aws_route_table.wireguard.id
}

output "route_table_arn" {
  description = "WireGuard route table ARN"
  value       = aws_route_table.wireguard.arn
}

# Subnet information
output "subnet_ids" {
  description = "List of all subnet IDs in the VPC"
  value       = data.aws_subnets.all_subnets.ids
}

output "subnet_details" {
  description = "Map of subnet IDs to their details"
  value = {
    for subnet_id, subnet in data.aws_subnet.all_subnet_details :
    subnet_id => {
      cidr_block        = subnet.cidr_block
      availability_zone = subnet.availability_zone
      tags              = subnet.tags
    }
  }
}