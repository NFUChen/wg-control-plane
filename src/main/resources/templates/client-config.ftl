[Interface]
PrivateKey = ${privateKey}
Address = ${address}
<#if dnsServers?has_content>DNS = ${dnsServers}</#if>
<#if mtu?has_content>MTU = ${mtu}</#if>

[Peer]
PublicKey = ${serverPublicKey}
AllowedIPs = ${allowedIPs}   <#-- e.g., 0.0.0.0/0 or server network range>
Endpoint = ${serverEndpoint}  <#-- server:port>
<#if persistentKeepalive gt 0>PersistentKeepalive = ${persistentKeepalive}</#if>     <#-- client keepalive heartbeat>