package com.app.utils

import org.springframework.stereotype.Component

/**
 * Interface for WireGuard key generation operations
 */
interface WireGuardKeyGenerator {
    /**
     * Generate new private key
     */
    fun generatePrivateKey(): String

    /**
     * Generate corresponding public key from private key
     */
    fun generatePublicKey(privateKey: String): String

    /**
     * Generate pre-shared key
     */
    fun generatePresharedKey(): String

    /**
     * Generate key pair (private key and public key)
     */
    fun generateKeyPair(): Pair<String, String>
}

/**
 * Default WireGuard key generator implementation using CommandExecutor
 */
@Component
class DefaultWireGuardKeyGenerator(
    private val commandExecutor: CommandExecutor
) : WireGuardKeyGenerator {

    private fun executeWgCommand(vararg command: String, input: String? = null): String {
        val result = commandExecutor.execute(*command, input = input)

        if (result.exitCode == 0 && result.output.isNotEmpty()) {
            return result.output
        } else {
            throw RuntimeException(
                "Command failed: ${command.joinToString(" ")}, exit code ${result.exitCode}, output: ${result.output}"
            )
        }
    }

    override fun generatePrivateKey(): String = executeWgCommand("wg", "genkey")

    override fun generatePublicKey(privateKey: String): String = executeWgCommand("wg", "pubkey", input = privateKey)

    override fun generatePresharedKey(): String = executeWgCommand("wg", "genpsk")

    override fun generateKeyPair(): Pair<String, String> {
        val privateKey = generatePrivateKey()
        val publicKey = generatePublicKey(privateKey)
        return privateKey to publicKey
    }
}
