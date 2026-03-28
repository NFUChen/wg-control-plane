[Interface]
PrivateKey = ${privateKey}
Address = ${address}
<#if listenPort??>ListenPort = ${listenPort}</#if>

[Peer]
PublicKey = ${serverPublicKey}
AllowedIPs = ${allowedIPs}   <#-- 例如 0.0.0.0/0 或 server 網段>
Endpoint = ${serverEndpoint}  <#-- server:port>
PersistentKeepalive = 25     <#-- client 常用保持連線心跳>