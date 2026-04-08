# Create single route table for WireGuard traffic
resource "azurerm_route_table" "wireguard" {
  name                = var.route_table_name
  location            = data.azurerm_virtual_network.this.location
  resource_group_name = data.azurerm_virtual_network.this.resource_group_name

  tags = merge(
    {
      Purpose = "WireGuard routing"
    },
    var.tags
  )
}

# Create routes pointing to WireGuard NIC for each destination CIDR
resource "azurerm_route" "to_wireguard" {
  for_each = toset(var.destination_cidrs)

  name                = "route-to-wireguard-${replace(each.value, "/", "-")}"
  resource_group_name = data.azurerm_virtual_network.this.resource_group_name
  route_table_name    = azurerm_route_table.wireguard.name

  address_prefix      = each.value
  next_hop_type       = "VirtualAppliance"
  next_hop_in_ip_address = data.azurerm_network_interface.wireguard.private_ip_address
}

# Associate route table with all subnets in the VNet
resource "azurerm_subnet_route_table_association" "auto_associate" {
  for_each = data.azurerm_subnet.all_subnet_details

  subnet_id      = each.value.id
  route_table_id = azurerm_route_table.wireguard.id
}