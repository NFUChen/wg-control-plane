[Interface]
PrivateKey = ${privateKey}
Address = ${address}
ListenPort = ${listenPort}

<#list peers as peer>
[Peer]
PublicKey = ${peer.publicKey}
AllowedIPs = ${peer.allowedIPs}
Endpoint = ${peer.endpoint}

</#list>