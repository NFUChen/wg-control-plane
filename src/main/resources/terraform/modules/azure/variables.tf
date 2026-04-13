variable "wireguard_nic_id" {
  description = "The Network Interface ID of the WireGuard server to route traffic to"
  type        = string
}

variable "destination_cidrs" {
  description = "List of CIDR blocks to route to the WireGuard Network Interface"
  type        = list(string)
  default     = []
}

variable "route_table_name" {
  description = "Name for the WireGuard route table"
  type        = string
  default     = "wireguard-route-table"
}

variable "tags" {
  description = "Tags to apply to resources"
  type        = map(string)
  default     = {}
}