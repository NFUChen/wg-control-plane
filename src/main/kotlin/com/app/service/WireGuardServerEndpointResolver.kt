package com.app.service

import com.app.model.AnsibleHost
import com.app.model.GlobalConfig
import com.app.model.WireGuardServer
import com.app.repository.AnsibleHostRepository
import com.app.service.WireGuardServerEndpointResolver.Companion.formatHostPort
import org.springframework.stereotype.Component

interface WireGuardServerEndpointResolver {
    fun resolve(server: WireGuardServer, globalConfig: GlobalConfig): String

    companion object {
        /**
         * WireGuard requires IPv6 endpoints as `[addr]:port`; IPv4 and hostnames use `addr:port`.
         */
        fun formatHostPort(untrimmedHost: String, listenPort: Int): String {
            val host = untrimmedHost.trim()
            if (host.contains("]:")) {
                return host
            }
            if (host.startsWith("[") && host.endsWith("]")) {
                return "$host:$listenPort"
            }
            if (host.contains(':')) {
                return "[$host]:$listenPort"
            }
            return "$host:$listenPort"
        }
    }
}

/**
 * Resolves the WireGuard [Peer] Endpoint for client configs and API responses.
 * Local servers (no [WireGuardServer.hostId]) use the global public endpoint;
 * remote Ansible-backed servers use that host's actual IP and this server's listen port.
 */
@Component
class DefaultWireGuardServerEndpointResolver(
    private val ansibleHostRepository: AnsibleHostRepository
): WireGuardServerEndpointResolver {

    override fun resolve(server: WireGuardServer, globalConfig: GlobalConfig): String {
        val hostId = server.hostId ?: return globalConfig.serverEndpoint
        val host = ansibleHostRepository.findById(hostId).orElseThrow {
            IllegalStateException("Ansible host not found for WireGuard server (hostId=$hostId)")
        }
        return formatEndpoint(host, server.listenPort)
    }

    private fun formatEndpoint(host: AnsibleHost, listenPort: Int): String =
        formatHostPort(host.ipAddress.trim(), listenPort)
}
