package com.app.service

import com.app.model.IPAddress
import com.app.model.WireGuardClient
import com.app.model.WireGuardServer
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

        logger.debug("Validating ${newClientIPs.size} new IPs against ${activeClients.size} existing clients and server addresses")

        // Parse new client IPs using IPAddress library
        val newAddresses = newClientIPs.map { ip ->
            try {
                IPAddressString(ip.address).address
                    ?: throw IllegalArgumentException("Invalid IP address format: ${ip.address}")
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid IP address format: ${ip.address} - ${e.message}")
            }
        }

        // Validate each new IP against server addresses and existing clients
        newClientIPs.zip(newAddresses) { newIP, newAddr ->
            validateAgainstServerAddresses(newIP, newAddr, server)
            validateAgainstExistingClients(newIP, newAddr, activeClients)
        }

        logger.debug("IP validation completed successfully")
    }

    /**
     * Validate new IP against server addresses
     */
    private fun validateAgainstServerAddresses(
        newIP: IPAddress,
        newAddr: inet.ipaddr.IPAddress,
        server: WireGuardServer
    ) {
        server.addresses.forEach { serverIP ->
            parseAndCheckConflict(
                existingIP = serverIP,
                newIP = newIP,
                newAddr = newAddr,
                context = "server",
                entityName = null
            )
        }
    }

    /**
     * Validate new IP against existing client addresses
     */
    private fun validateAgainstExistingClients(
        newIP: IPAddress,
        newAddr: inet.ipaddr.IPAddress,
        activeClients: List<WireGuardClient>
    ) {
        activeClients.forEach { existingClient ->
            existingClient.allowedIPs.forEach { existingIP ->
                parseAndCheckConflict(
                    existingIP = existingIP,
                    newIP = newIP,
                    newAddr = newAddr,
                    context = "client",
                    entityName = existingClient.name
                )
            }
        }
    }

    /**
     * Parse existing IP and check for conflicts with new IP
     */
    private fun parseAndCheckConflict(
        existingIP: IPAddress,
        newIP: IPAddress,
        newAddr: inet.ipaddr.IPAddress,
        context: String,
        entityName: String?
    ) {
        try {
            val existingAddr = IPAddressString(existingIP.address).address
                ?: throw IllegalArgumentException("Invalid $context IP: ${existingIP.address}")

            checkIPAddressConflict(newIP, newAddr, existingIP, existingAddr, context, entityName)

        } catch (e: IllegalArgumentException) {
            throw e // Re-throw validation errors
        } catch (e: Exception) {
            val logContext = if (entityName != null) "$context $entityName" else context
            logger.warn("Failed to parse $logContext IP ${existingIP.address}: ${e.message}")
            // Continue with next IP rather than failing completely
        }
    }

    /**
     * Check for IP address conflicts between new and existing addresses
     */
    private fun checkIPAddressConflict(
        newIP: IPAddress,
        newAddr: inet.ipaddr.IPAddress,
        existingIP: IPAddress,
        existingAddr: inet.ipaddr.IPAddress,
        context: String,
        entityName: String?
    ) {
        when {
            // Exact match
            newAddr == existingAddr -> {
                val target = if (context == "server") "server" else "client '$entityName'"
                throw IllegalArgumentException(
                    "IP address conflict: ${newIP.address} is already assigned to $target"
                )
            }
            // New IP contains existing (new is supernet)
            newAddr.contains(existingAddr) -> {
                val target = if (context == "server") "server IP ${existingIP.address}" else "existing client '$entityName' IP ${existingIP.address}"
                throw IllegalArgumentException(
                    "IP address conflict: ${newIP.address} would ${if (context == "server") "contain" else "conflict with"} $target"
                )
            }
            // Existing contains new (new is subnet)
            existingAddr.contains(newAddr) -> {
                val target = if (context == "server") "server IP range ${existingIP.address}" else "existing client '$entityName' IP ${existingIP.address}"
                throw IllegalArgumentException(
                    "IP address conflict: ${newIP.address} conflicts with $target"
                )
            }
        }
    }
}