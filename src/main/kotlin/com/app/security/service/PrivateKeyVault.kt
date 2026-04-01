package com.app.security.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secure vault service for encrypting/decrypting private keys
 * Works with PrivateKey entities for database storage
 */
@Component
class PrivateKeyVault {

    companion object {
        private val logger = LoggerFactory.getLogger(PrivateKeyVault::class.java)
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }

    private val masterKey: SecretKey = generateMasterKey()
    private val random = SecureRandom()

    /**
     * Encrypt a private key for database storage
     */
    fun encryptPrivateKey(privateKey: String): EncryptedKeyData {
        return try {
            val iv = ByteArray(GCM_IV_LENGTH)
            random.nextBytes(iv)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec)

            val encrypted = cipher.doFinal(privateKey.toByteArray(StandardCharsets.UTF_8))

            // Combine IV + encrypted data, then encode to Base64
            val combined = iv + encrypted
            val encodedData = Base64.getEncoder().encodeToString(combined)

            EncryptedKeyData(
                encryptedData = encodedData,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logger.error("Failed to encrypt private key", e)
            throw IllegalStateException("Private key encryption failed", e)
        }
    }

    /**
     * Decrypt a private key from database storage
     */
    fun decryptPrivateKey(encryptedData: String): String {
        return try {
            val combined = Base64.getDecoder().decode(encryptedData)

            // Extract IV and encrypted data
            val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
            val encrypted = combined.sliceArray(GCM_IV_LENGTH until combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)

            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            logger.error("Failed to decrypt private key", e)
            throw IllegalStateException("Private key decryption failed", e)
        }
    }

    /**
     * Encrypt a passphrase for database storage
     */
    fun encryptPassphrase(passphrase: String): String {
        return try {
            val iv = ByteArray(GCM_IV_LENGTH)
            random.nextBytes(iv)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec)

            val encrypted = cipher.doFinal(passphrase.toByteArray(StandardCharsets.UTF_8))

            // Combine IV + encrypted data, then encode to Base64
            val combined = iv + encrypted
            Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            logger.error("Failed to encrypt passphrase", e)
            throw IllegalStateException("Passphrase encryption failed", e)
        }
    }

    /**
     * Decrypt a passphrase from database storage
     */
    fun decryptPassphrase(encryptedPassphrase: String): String {
        return try {
            val combined = Base64.getDecoder().decode(encryptedPassphrase)

            // Extract IV and encrypted data
            val iv = combined.sliceArray(0 until GCM_IV_LENGTH)
            val encrypted = combined.sliceArray(GCM_IV_LENGTH until combined.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)

            val decrypted = cipher.doFinal(encrypted)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            logger.error("Failed to decrypt passphrase", e)
            throw IllegalStateException("Passphrase decryption failed", e)
        }
    }

    /**
     * Encrypt a private key for secure transport (different key for each transport)
     */
    fun encryptForTransport(privateKey: String): SecureTransportPackage {
        return try {
            val transportKey = generateTransportKey()
            val iv = ByteArray(GCM_IV_LENGTH)
            random.nextBytes(iv)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.ENCRYPT_MODE, transportKey, spec)

            val encrypted = cipher.doFinal(privateKey.toByteArray(StandardCharsets.UTF_8))

            // Generate checksum for integrity verification
            val checksum = generateChecksum(privateKey)

            SecureTransportPackage(
                encryptedKey = Base64.getEncoder().encodeToString(encrypted),
                transportKey = Base64.getEncoder().encodeToString(transportKey.encoded),
                iv = Base64.getEncoder().encodeToString(iv),
                checksum = checksum,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logger.error("Failed to encrypt key for transport", e)
            throw IllegalStateException("Transport encryption failed", e)
        }
    }

    /**
     * Decrypt a private key from transport package
     */
    fun decryptFromTransport(transportPackage: SecureTransportPackage): String {
        return try {
            val transportKeyBytes = Base64.getDecoder().decode(transportPackage.transportKey)
            val iv = Base64.getDecoder().decode(transportPackage.iv)
            val encrypted = Base64.getDecoder().decode(transportPackage.encryptedKey)

            val transportKey = SecretKeySpec(transportKeyBytes, ALGORITHM)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, transportKey, spec)

            val decrypted = cipher.doFinal(encrypted)
            val privateKey = String(decrypted, StandardCharsets.UTF_8)

            // Verify integrity
            val computedChecksum = generateChecksum(privateKey)
            if (computedChecksum != transportPackage.checksum) {
                throw IllegalStateException("Transport package integrity verification failed")
            }

            privateKey
        } catch (e: Exception) {
            logger.error("Failed to decrypt key from transport", e)
            throw IllegalStateException("Transport decryption failed", e)
        }
    }

    /**
     * Generate SSH key fingerprint
     */
    fun generateFingerprint(publicKey: String): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(publicKey.toByteArray(StandardCharsets.UTF_8))
            "SHA256:" + Base64.getEncoder().encodeToString(hash)
        } catch (e: Exception) {
            logger.error("Failed to generate fingerprint", e)
            ""
        }
    }

    /**
     * Validate private key format
     */
    fun validatePrivateKeyFormat(privateKey: String, expectedFormat: String): Boolean {
        return when (expectedFormat.uppercase()) {
            "OPENSSH" -> privateKey.contains("BEGIN OPENSSH PRIVATE KEY")
            "PEM", "PKCS8" -> privateKey.contains("BEGIN PRIVATE KEY") ||
                            privateKey.contains("BEGIN RSA PRIVATE KEY") ||
                            privateKey.contains("BEGIN EC PRIVATE KEY")
            "WIREGUARD" -> privateKey.matches(Regex("^[A-Za-z0-9+/]{43}=$"))
            else -> true // Unknown format, assume valid
        }
    }

    /**
     * Create temporary private key file for Ansible execution
     * Returns the file path and a cleanup function
     */
    fun createTempPrivateKeyFile(encryptedPrivateKey: String): TempKeyFile {
        try {
            // Decrypt the private key
            val privateKey = decryptPrivateKey(encryptedPrivateKey)

            // Create secure temporary file
            val tempFile = java.nio.file.Files.createTempFile("wireguard_key_", ".pem")

            // Set restrictive permissions (600)
            val file = tempFile.toFile()
            file.setReadable(false, false)
            file.setWritable(false, false)
            file.setExecutable(false, false)
            file.setReadable(true, true)
            file.setWritable(true, true)

            // Write private key to file
            file.writeText(privateKey)

            logger.debug("Created temporary private key file: ${tempFile.toAbsolutePath()}")

            return TempKeyFile(
                filePath = tempFile.toAbsolutePath().toString(),
                cleanup = {
                    try {
                        if (file.exists()) {
                            // Overwrite with random data before deletion for security
                            val randomData = ByteArray(file.length().toInt())
                            random.nextBytes(randomData)
                            file.writeBytes(randomData)
                            file.delete()
                            logger.debug("Cleaned up temporary private key file: ${tempFile.toAbsolutePath()}")
                        }
                    } catch (e: Exception) {
                        logger.error("Failed to cleanup temporary private key file", e)
                    }
                }
            )
        } catch (e: Exception) {
            logger.error("Failed to create temporary private key file", e)
            throw IllegalStateException("Temporary key file creation failed", e)
        }
    }

    private fun generateMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        keyGenerator.init(256) // AES-256
        return keyGenerator.generateKey()
    }

    private fun generateTransportKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
        keyGenerator.init(256) // AES-256
        return keyGenerator.generateKey()
    }

    private fun generateChecksum(data: String): String {
        return Base64.getEncoder().encodeToString(
            data.toByteArray(StandardCharsets.UTF_8).let { bytes ->
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            }
        )
    }
}

/**
 * Data class for encrypted key storage result
 */
data class EncryptedKeyData(
    val encryptedData: String,
    val timestamp: Long
)

/**
 * Secure transport package for Ansible deployment
 */
data class SecureTransportPackage(
    val encryptedKey: String,
    val transportKey: String,
    val iv: String,
    val checksum: String,
    val timestamp: Long
)