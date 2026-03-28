[Interface]
PrivateKey = ${privateKey}
Address = ${address}
ListenPort = ${listenPort}   <#-- 可選，client 一般不需要指定 port>

[Peer]
PublicKey = ${serverPublicKey}
AllowedIPs = ${allowedIPs}   <#-- 例如 0.0.0.0/0 或 server 網段>
Endpoint = ${serverEndpoint}  <#-- server:port>
PersistentKeepalive = 25     <#-- client 常用保持連線心跳>