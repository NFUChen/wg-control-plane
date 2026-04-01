package com.app.service

import com.app.model.WireGuardClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Service for executing WireGuard CLI commands dynamically
 */
@Service
class WireGuardCommandService {

    companion object {
        private val logger = LoggerFactory.getLogger(WireGuardCommandService::class.java)
    }

    fun launchWireGuardInterface(interfaceName: String) {
        val command = listOf("wg-quick", "up", interfaceName)

        logger.info("Launching WireGuard interface $interfaceName")
        val result = executeWgCommand(command)
        if (result.exitCode != 0) {
            throw RuntimeException("Failed to launch WireGuard interface $interfaceName: ${result.output}")
        }

        logger.info("Successfully launched WireGuard interface $interfaceName")
    }

    /**
     * Add peer to WireGuard interface dynamically
     */
    fun addPeerToInterface(interfaceName: String, client: WireGuardClient) {
        val command = mutableListOf("wg", "set", interfaceName, "peer", client.publicKey)

        // Add allowed IPs
        if (client.allowedIPs.isNotEmpty()) {
            command.addAll(listOf("allowed-ips", client.plainTextAllowedIPs))
        }

        // Add preshared key if present
        client.presharedKey?.let { psk ->
            if (psk.isNotBlank()) {
                command.addAll(listOf("preshared-key", psk))
            }
        }

        if (client.persistentKeepalive > 0) {
            command.addAll(listOf("persistent-keepalive", client.persistentKeepalive.toString()))
        }

        logger.info("Adding peer ${client.name} (${client.publicKey.take(8)}...) to interface $interfaceName with allowed IPs: ${client.plainTextAllowedIPs}")
        val result = executeWgCommand(command)
        if (result.exitCode != 0) {
            throw RuntimeException("Failed to add peer to interface $interfaceName: ${result.output}")
        }

        logger.info("Successfully added peer ${client.name} (${client.publicKey.take(8)}...) to interface $interfaceName")
    }

    /**
     * Remove peer from WireGuard interface dynamically
     */
    fun removePeerFromInterface(interfaceName: String, publicKey: String) {
        val command = listOf("wg", "set", interfaceName, "peer", publicKey, "remove")

        val result = executeWgCommand(command)
        if (result.exitCode != 0) {
            logger.warn("Failed to remove peer from interface $interfaceName: ${result.output}")
        } else {
            logger.info("Successfully removed peer (${publicKey.take(8)}...) from interface $interfaceName")
        }
    }


    /**
     * Stop WireGuard interface
     */
    fun stopWireGuardInterface(interfaceName: String) {
        val command = listOf("wg-quick", "down", interfaceName)

        logger.info("Stopping WireGuard interface $interfaceName")
        val result = executeWgCommand(command)
        if (result.exitCode != 0) {
            logger.warn("Failed to stop WireGuard interface $interfaceName: ${result.output}")
        } else {
            logger.info("Successfully stopped WireGuard interface $interfaceName")
        }
    }

    /**
     * Check if WireGuard interface is currently running
     */
    fun isInterfaceRunning(interfaceName: String): Boolean {
        return try {
            val command = listOf("wg", "show", interfaceName)
            val result = executeWgCommand(command)
            result.exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Execute WireGuard command with timeout
     */
    private fun executeWgCommand(command: List<String>): ProcessResult {
        return try {
            logger.info("Executing WireGuard command: ${command.joinToString(" ")}")

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = if (process.waitFor(10, TimeUnit.SECONDS)) {
                process.exitValue()
            } else {
                process.destroyForcibly()
                throw IOException("WireGuard command timed out")
            }

            ProcessResult(exitCode, output.trim())
        } catch (e: Exception) {
            logger.error("Failed to execute WireGuard command: ${command.joinToString(" ")}", e)
            throw RuntimeException("WireGuard command execution failed: ${e.message}", e)
        }
    }

    /**
     * Process execution result
     */
    private data class ProcessResult(val exitCode: Int, val output: String)
}