# Get Network Interface information to extract VNet details
data "azurerm_network_interface" "wireguard" {
  name                = split("/", var.wireguard_nic_id)[8]  # Extract NIC name from resource ID
  resource_group_name = split("/", var.wireguard_nic_id)[4]  # Extract RG name from resource ID
}

# Get Virtual Network information from the Network Interface
data "azurerm_virtual_network" "this" {
  name                = split("/", data.azurerm_network_interface.wireguard.ip_configuration[0].subnet_id)[8]
  resource_group_name = split("/", data.azurerm_network_interface.wireguard.ip_configuration[0].subnet_id)[4]
}

# Get all subnets in the Virtual Network
data "azurerm_subnets" "all_subnets" {
  resource_group_name  = data.azurerm_virtual_network.this.resource_group_name
  virtual_network_name = data.azurerm_virtual_network.this.name
}

# Get details for each subnet
data "azurerm_subnet" "all_subnet_details" {
  for_each = toset(data.azurerm_subnets.all_subnets.names)

  name                 = each.value
  virtual_network_name = data.azurerm_virtual_network.this.name
  resource_group_name  = data.azurerm_virtual_network.this.resource_group_name
}