package com.module.wgcontrolplane.service

import com.module.wgcontrolplane.model.IPAddress
import com.module.wgcontrolplane.model.WireGuardServer
import inet.ipaddr.IPAddressString
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * CIDR overlap detection service using seancfoley/IPAddress library
 * Properly handles prefix conflicts using proven algorithms
 */
@Service
class IPConflictDetectionService {

    companion object {
        private val logger = LoggerFactory.getLogger(IPConflictDetectionService::class.java)
    }

    /**
     * Check if adding a client with the given IP addresses would cause CIDR overlaps
     * Uses IPAddress library for proper prefix conflict detection
     * @param server The server to add the client to
     * @param newClientIPs The IP addresses for the new client
     * @throws IllegalArgumentException if there are conflicts
     */
    fun validateNewClientIPs(server: WireGuardServer, newClientIPs: List<IPAddress>) {
        validateClientIPsAgainstPeers(server, newClientIPs, excludeClientId = null)
    }

    /**
     * Validate IPs for an existing client being updated (exclude that client from conflict checks).
     */
    fun validateUpdatedClientIPs(server: WireGuardServer, updatingClientId: UUID, newClientIPs: List<IPAddress>) {
        validateClientIPsAgainstPeers(server, newClientIPs, excludeClientId = updatingClientId)
    }

    private fun validateClientIPsAgainstPeers(
        server: WireGuardServer,
        newClientIPs: List<IPAddress>,
        excludeClientId: UUID?
    ) {
        val activeClients = server.clients.filter {
            it.enabled && (excludeClientId == null || it.id != excludeClientId)
        }

        logger.debug("Validating ${newClientIPs.size} new IPs against ${activeClients.size} existing clients")

        // Parse new client IPs using IPAddress library
        val newAddresses = newClientIPs.map { ip ->
            try {
                IPAddressString(ip.address).address
                    ?: throw IllegalArgumentException("Invalid IP address format: ${ip.address}")
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid IP address format: ${ip.address} - ${e.message}")
            }
        }

        // Validate each new IP
        newClientIPs.forEachIndexed { index, newIP ->
            val newAddr = newAddresses[index]

            // Check for CIDR overlaps with existing clients
            activeClients.forEach { existingClient ->
                existingClient.allowedIPs.forEach { existingIP ->
                    try {
                        val existingAddr = IPAddressString(existingIP.address).address
                            ?: throw IllegalArgumentException("Invalid existing IP: ${existingIP.address}")

                        // Check for overlap in either direction
                        when {
                            // Exact match
                            newAddr.equals(existingAddr) -> {
                                throw IllegalArgumentException(
                                    "IP ${newIP.address} exactly matches existing IP for client '${existingClient.name}'"
                                )
                            }
                            // New IP contains existing (new is supernet)
                            newAddr.contains(existingAddr) -> {
                                throw IllegalArgumentException(
                                    "IP ${newIP.address} would contain existing client '${existingClient.name}' IP ${existingIP.address}"
                                )
                            }
                            // Existing contains new (new is subnet)
                            existingAddr.contains(newAddr) -> {
                                throw IllegalArgumentException(
                                    "IP ${newIP.address} is contained within existing client '${existingClient.name}' IP ${existingIP.address}"
                                )
                            }
                        }
                    } catch (e: IllegalArgumentException) {
                        throw e // Re-throw our validation errors
                    } catch (e: Exception) {
                        logger.warn("Failed to parse existing IP ${existingIP.address} for client ${existingClient.name}: ${e.message}")
                        // Continue with next IP rather than failing completely
                    }
                }
            }
        }

        logger.debug("IP validation completed successfully")
    }
}