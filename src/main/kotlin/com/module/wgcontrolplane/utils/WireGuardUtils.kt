package com.module.wgcontrolplane.utils


/**
 * WireGuard utility class, encapsulates command line operations
 */
object WireGuardUtils {

    /**
     * Execute WireGuard command
     */
    private fun executeWgCommand(vararg command: String, input: String? = null): String {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()

            // If input data is needed, write to stdin
            input?.let { inputData ->
                process.outputStream.use { outputStream ->
                    outputStream.write(inputData.toByteArray())
                    outputStream.flush()
                }
            }

            // Wait for process completion and read result
            val exitCode = process.waitFor()
            val result = process.inputStream.bufferedReader().use { it.readText().trim() }

            if (exitCode == 0 && result.isNotEmpty()) {
                result
            } else {
                throw RuntimeException("Command failed: ${command.joinToString(" ")}, exit code $exitCode, output: $result")
            }
        } catch (e: Exception) {
            throw RuntimeException("Error executing WireGuard command: ${command.joinToString(" ")}", e)
        }
    }

    /**
     * Generate new private key
     */
    fun generatePrivateKey(): String = executeWgCommand("wg", "genkey")

    /**
     * Generate corresponding public key from private key
     */
    fun generatePublicKey(privateKey: String): String = executeWgCommand("wg", "pubkey", input = privateKey)

    /**
     * Generate pre-shared key
     */
    fun generatePresharedKey(): String = executeWgCommand("wg", "genpsk")

    /**
     * Generate key pair (private key and public key)
     */
    fun generateKeyPair(): Pair<String, String> {
        val privateKey = generatePrivateKey()
        val publicKey = generatePublicKey(privateKey)
        return privateKey to publicKey
    }
}
