[Interface]
PrivateKey = ${privateKey}
Address = ${address}
<#if listenPort??>ListenPort = ${listenPort?c}</#if>
<#if dnsServers?has_content>DNS = ${dnsServers}</#if>
<#if mtu?has_content>MTU = ${mtu?c}</#if>
<#if postUp?has_content>PostUp = ${postUp}</#if>
<#if postDown?has_content>PostDown = ${postDown}</#if>

<#list clients as client>
<#if client.enabled>
# Client: ${client.publicKey?substring(0, 8)}...
[Peer]
PublicKey = ${client.publicKey}
AllowedIPs = ${client.allowedIPs}
<#if client.presharedKey?has_content>PresharedKey = ${client.presharedKey}</#if>
<#if client.persistentKeepalive gt 0>PersistentKeepalive = ${client.persistentKeepalive?c}</#if>

</#if>
</#list>