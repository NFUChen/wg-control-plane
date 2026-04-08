# Virtual Network information
output "vnet_id" {
  description = "Virtual Network ID where WireGuard is deployed"
  value       = data.azurerm_virtual_network.this.id
}

output "vnet_name" {
  description = "Virtual Network name"
  value       = data.azurerm_virtual_network.this.name
}

output "vnet_address_space" {
  description = "Address space of the Virtual Network"
  value       = data.azurerm_virtual_network.this.address_space
}

output "resource_group_name" {
  description = "Resource Group name"
  value       = data.azurerm_virtual_network.this.resource_group_name
}

# WireGuard Network Interface information
output "wireguard_nic_id" {
  description = "WireGuard Network Interface ID"
  value       = var.wireguard_nic_id
}

output "wireguard_nic_private_ip" {
  description = "Private IP address of WireGuard Network Interface"
  value       = data.azurerm_network_interface.wireguard.private_ip_address
}

# Route table
output "route_table_id" {
  description = "WireGuard route table ID"
  value       = azurerm_route_table.wireguard.id
}

output "route_table_name" {
  description = "WireGuard route table name"
  value       = azurerm_route_table.wireguard.name
}

# Subnet information
output "subnet_names" {
  description = "List of all subnet names in the VNet"
  value       = data.azurerm_subnets.all_subnets.names
}

output "subnet_details" {
  description = "Map of subnet names to their details"
  value = {
    for name, subnet in data.azurerm_subnet.all_subnet_details :
    name => {
      id            = subnet.id
      address_prefixes = subnet.address_prefixes
    }
  }
}