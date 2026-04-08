# WireGuard Client Configuration - Site-to-Site Mesh Network
# Generated: ${.now}
#
# Network Topology - This client can reach:
<#list networkTopology as network>
#   ${network}
</#list>
#
# Total connected clients: ${otherClientsCount + 1} (including this client)
# Traffic routing: Client → Server → Target Client/Network
#

[Interface]
PrivateKey = ${privateKey}
Address = ${peerIP}
<#if dnsServers?has_content>DNS = ${dnsServers}</#if>

[Peer]
# Central server - routes traffic to all client networks
PublicKey = ${serverPublicKey}
AllowedIPs = ${allowedIPs}
Endpoint = ${serverEndpoint}
<#if persistentKeepalive?number gt 0>PersistentKeepalive = ${persistentKeepalive?c}</#if>