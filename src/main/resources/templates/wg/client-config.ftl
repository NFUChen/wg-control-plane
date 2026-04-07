[Interface]
PrivateKey = ${privateKey}
Address = ${peerIP}
<#if dnsServers?has_content>DNS = ${dnsServers}</#if>
<#if mtu?has_content>MTU = ${mtu?c}</#if>

[Peer]
PublicKey = ${serverPublicKey}
AllowedIPs = ${allowedIPs}
Endpoint = ${serverEndpoint}
<#if persistentKeepalive?number gt 0>PersistentKeepalive = ${persistentKeepalive?c}</#if>