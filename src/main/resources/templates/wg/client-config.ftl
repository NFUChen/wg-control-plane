[Interface]
PrivateKey = ${privateKey}
Address = ${peerIP}
<#if dnsServers?has_content>DNS = ${dnsServers}</#if>

[Peer]
PublicKey = ${serverPublicKey}
AllowedIPs = ${allowedIPs}
Endpoint = ${serverEndpoint}
<#if persistentKeepalive?number gt 0>PersistentKeepalive = ${persistentKeepalive?c}</#if>