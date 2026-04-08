
# Get ENI information to extract VPC ID
data "aws_network_interface" "wireguard" {
  id = var.wireguard_eni_id
}

# Get VPC information from the ENI
data "aws_vpc" "this" {
  id = data.aws_network_interface.wireguard.vpc_id
}

data "aws_subnets" "all_subnets" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.this.id]
  }
}

data "aws_subnet" "all_subnet_details" {
  for_each = toset(data.aws_subnets.all_subnets.ids)
  id       = each.value
}