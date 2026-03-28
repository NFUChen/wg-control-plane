package com.module.wgcontrolplane.service

import com.module.wgcontrolplane.model.*
import freemarker.template.Configuration
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.StringWriter

@Service
class TemplateService(@Qualifier("templateConfiguration") private val freemarkerConfig: Configuration) {

    /**
     * 生成服务端配置 - 直接接收 WgInterface 和 peers
     */
    fun generateServerConfig(serverInterface: WgInterface, peers: List<WgPeer>): String {
        val dataModel: Map<String, Any> = mapOf(
            "privateKey" to serverInterface.privateKey,
            "address" to serverInterface.address.joinToString(", ") { it.address },
            "listenPort" to serverInterface.listenPort,
            "peers" to peers.map { it.toTemplateMap() }
        )

        return renderTemplate("server-config.ftl", dataModel)
    }

    /**
     * 生成客户端配置 - 客户端连接到服务端
     */
    fun generateClientConfig(
        clientInterface: WgInterface,
        serverPublicKey: String,
        serverEndpoint: String,
        allowedIPs: List<IPAddress> = listOf(IPAddress("0.0.0.0/0"))
    ): String {
        val dataModel = mutableMapOf<String, Any>(
            "privateKey" to clientInterface.privateKey,
            "address" to clientInterface.address.joinToString(", ") { it.address },
            "serverPublicKey" to serverPublicKey,
            "serverEndpoint" to serverEndpoint,
            "allowedIPs" to allowedIPs.joinToString(", ") { it.address }
        )

        // 只有在需要监听端口时才添加
        if (clientInterface.listenPort > 0) {
            dataModel["listenPort"] = clientInterface.listenPort
        }

        return renderTemplate("client-config.ftl", dataModel)
    }

    /**
     * 生成客户端配置 - 使用服务端 interface 自动获取 public key
     */
    fun generateClientConfig(
        clientInterface: WgInterface,
        serverInterface: WgInterface,
        serverEndpoint: String,
        allowedIPs: List<IPAddress> = listOf(IPAddress("0.0.0.0/0"))
    ): String {
        return generateClientConfig(
            clientInterface = clientInterface,
            serverPublicKey = serverInterface.publicKey,
            serverEndpoint = serverEndpoint,
            allowedIPs = allowedIPs
        )
    }

    /**
     * 渲染指定模板
     */
    private fun renderTemplate(templateName: String, dataModel: Map<String, Any>): String {
        val template = freemarkerConfig.getTemplate(templateName)
        val out = StringWriter()
        template.process(dataModel, out)
        return out.toString()
    }

    /**
     * 验证生成的配置格式
     */
    fun validateConfigFormat(configContent: String): List<String> {
        val errors = mutableListOf<String>()
        val lines = configContent.lines()

        var hasInterface = false

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed == "[Interface]" -> hasInterface = true
                trimmed.startsWith("PrivateKey =") && trimmed.length < 20 -> {
                    errors.add("Private key appears to be invalid or placeholder")
                }
                trimmed.startsWith("PublicKey =") && trimmed.length < 20 -> {
                    errors.add("Public key appears to be invalid or placeholder")
                }
            }
        }

        if (!hasInterface) {
            errors.add("Configuration missing [Interface] section")
        }

        return errors
    }

    /**
     * 生成配置 Hash (用于配置比较和缓存)
     */
    fun generateConfigHash(configContent: String): String = configContent.hashCode().toString()
}

/**
 * 扩展函数：WgPeer 转为模板数据
 */
private fun WgPeer.toTemplateMap(): Map<String, Any> = mapOf(
    "publicKey" to publicKey,
    "allowedIPs" to allowedIPs.joinToString(", ") { it.address },
    "endpoint" to endpoint,
    "presharedKey" to (presharedKey ?: ""),
    "persistentKeepalive" to persistentKeepalive
)