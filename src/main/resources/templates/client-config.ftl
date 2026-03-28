[Interface]
PrivateKey = ${privateKey}
Address = ${address}
<#if listenPort??>ListenPort = ${listenPort}</#if>

[Peer]
PublicKey = ${serverPublicKey}
AllowedIPs = ${allowedIPs}   <#-- e.g., 0.0.0.0/0 or server network range>
Endpoint = ${serverEndpoint}  <#-- server:port>
PersistentKeepalive = 25     <#-- common client keepalive heartbeat>