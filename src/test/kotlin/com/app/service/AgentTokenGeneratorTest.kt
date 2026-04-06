package com.app.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.test.context.ActiveProfiles
import kotlin.test.*
import java.util.concurrent.*
import java.security.SecureRandom
import org.mockito.Mock
import org.mockito.kotlin.*
import java.math.BigInteger

/**
 * Comprehensive tests for AgentTokenGenerator
 * Tests token generation, uniqueness, format, and thread safety
 */
@ExtendWith(MockitoExtension::class)
@ActiveProfiles("test")
class AgentTokenGeneratorTest {

    private lateinit var tokenGenerator: DefaultAgentTokenGenerator

    @Mock
    private lateinit var mockRandom: SecureRandom

    @BeforeEach
    fun setUp() {
        tokenGenerator = DefaultAgentTokenGenerator()
    }

    // ========== Basic Token Generation Tests ==========

    @Test
    fun `should generate token with correct prefix`() {
        // When
        val token = tokenGenerator.generateToken("wg")

        // Then
        assertTrue(token.startsWith("wg-"))
        assertTrue(token.length > 3) // At least "wg-" + some random part
    }

    @Test
    fun `should generate token with custom prefix`() {
        // When
        val token = tokenGenerator.generateToken("custom-prefix")

        // Then
        assertTrue(token.startsWith("custom-prefix-"))
        assertTrue(token.length > "custom-prefix-".length)
    }

    @Test
    fun `should generate token with empty prefix`() {
        // When
        val token = tokenGenerator.generateToken("")

        // Then
        assertTrue(token.startsWith("-"))
        assertTrue(token.length > 1)
    }

    // ========== Token Uniqueness Tests ==========

    @Test
    fun `should generate unique tokens`() {
        // When
        val tokens = (1..100).map { tokenGenerator.generateToken("test") }

        // Then
        val uniqueTokens = tokens.toSet()
        assertEquals(100, uniqueTokens.size, "All generated tokens should be unique")
    }

    @RepeatedTest(10)
    fun `should generate different tokens on repeated calls`() {
        // Given
        val prefix = "repeat"

        // When
        val token1 = tokenGenerator.generateToken(prefix)
        val token2 = tokenGenerator.generateToken(prefix)

        // Then
        assertNotEquals(token1, token2, "Consecutive token generations should produce different results")
        assertTrue(token1.startsWith("$prefix-"))
        assertTrue(token2.startsWith("$prefix-"))
    }

    // ========== Token Format Tests ==========

    @ParameterizedTest
    @ValueSource(strings = ["wg", "wgc", "test", "server", "client", "admin", "api"])
    fun `should generate properly formatted tokens for various prefixes`(prefix: String) {
        // When
        val token = tokenGenerator.generateToken(prefix)

        // Then
        assertTrue(token.startsWith("$prefix-"), "Token should start with prefix and dash")
        val tokenPart = token.substringAfter("-")
        assertTrue(tokenPart.isNotEmpty(), "Token part after prefix should not be empty")
        assertTrue(tokenPart.length >= 10, "Token should be reasonably long for security")
    }

    @Test
    fun `should generate tokens with alphanumeric characters only`() {
        // When
        val tokens = (1..50).map { tokenGenerator.generateToken("test") }

        // Then
        tokens.forEach { token ->
            val tokenPart = token.substringAfter("-")
            assertTrue(
                tokenPart.isNotEmpty() && tokenPart.all { it.isLetterOrDigit() },
                "Token part should contain only alphanumeric characters: $tokenPart"
            )
        }
    }

    @Test
    fun `should generate tokens with reasonable length`() {
        // When
        val tokens = (1..20).map { tokenGenerator.generateToken("test") }

        // Then
        tokens.forEach { token ->
            val tokenPart = token.substringAfter("-")
            assertTrue(tokenPart.length >= 15, "Token should be at least 15 characters long")
            assertTrue(tokenPart.length <= 40, "Token should not be excessively long")
        }
    }

    // ========== Edge Cases Tests ==========

    @Test
    fun `should handle special characters in prefix`() {
        // Given
        val specialPrefixes = listOf("wg_server", "wg.client", "wg@test")

        // When & Then
        specialPrefixes.forEach { prefix ->
            val token = tokenGenerator.generateToken(prefix)
            assertTrue(token.startsWith("$prefix-"))
            assertTrue(token.length > prefix.length + 1)
        }
    }

    @Test
    fun `should handle very long prefix`() {
        // Given
        val longPrefix = "a".repeat(100)

        // When
        val token = tokenGenerator.generateToken(longPrefix)

        // Then
        assertTrue(token.startsWith("$longPrefix-"))
        assertTrue(token.length > longPrefix.length + 1)
    }

    @Test
    fun `should handle null prefix gracefully`() {
        // When & Then
        assertFailsWith<Exception> {
            tokenGenerator.generateToken(null as String)
        }
    }

    // ========== Thread Safety Tests ==========

    @Test
    fun `should be thread safe - concurrent token generation`() {
        // Given
        val numberOfThreads = 10
        val tokensPerThread = 20
        val executor = Executors.newFixedThreadPool(numberOfThreads)
        val allTokens = ConcurrentHashMap.newKeySet<String>()
        val latch = CountDownLatch(numberOfThreads)

        // When
        repeat(numberOfThreads) {
            executor.submit {
                try {
                    val tokens = (1..tokensPerThread).map {
                        tokenGenerator.generateToken("thread-test")
                    }
                    allTokens.addAll(tokens)
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for all threads to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All threads should complete within 10 seconds")

        // Then
        assertEquals(
            numberOfThreads * tokensPerThread,
            allTokens.size,
            "All tokens should be unique even when generated concurrently"
        )

        executor.shutdown()
    }

    // ========== Security Tests ==========

    @Test
    fun `should use SecureRandom for cryptographic strength`() {
        // Given
        val generator = DefaultAgentTokenGenerator()

        // When
        val field = DefaultAgentTokenGenerator::class.java.getDeclaredField("random")
        field.isAccessible = true
        val random = field.get(generator)

        // Then
        assertTrue(random is SecureRandom, "Should use SecureRandom for security")
    }

    @Test
    fun `should generate tokens with high entropy`() {
        // Generate multiple tokens and check for entropy
        val tokens = (1..1000).map {
            tokenGenerator.generateToken("entropy-test").substringAfter("-")
        }

        // Check that we have good character distribution
        val allChars = tokens.joinToString("").toCharArray()
        val uniqueChars = allChars.toSet()

        // Should have a good variety of characters (at least 15 different chars)
        assertTrue(uniqueChars.size >= 15, "Tokens should have high character diversity")
    }

    // ========== Performance Tests ==========

    @Test
    fun `should generate tokens efficiently`() {
        // Measure token generation performance
        val iterations = 10000
        val startTime = System.currentTimeMillis()

        // When
        repeat(iterations) {
            tokenGenerator.generateToken("perf-test")
        }

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        val avgTime = totalTime.toDouble() / iterations

        // Then
        assertTrue(avgTime < 1.0, "Average token generation should be under 1ms: ${avgTime}ms")
        assertTrue(totalTime < 5000, "Total time for $iterations tokens should be under 5 seconds: ${totalTime}ms")
    }

    // ========== Integration-style Tests ==========

    @Test
    fun `should work with common WireGuard prefixes`() {
        // Given
        val wireGuardPrefixes = listOf("wg", "wgc", "wg-server", "wg-client")

        // When & Then
        wireGuardPrefixes.forEach { prefix ->
            val token = tokenGenerator.generateToken(prefix)
            assertTrue(token.startsWith("$prefix-"))

            val tokenPart = token.substringAfter("-")
            assertTrue(tokenPart.length >= 10) // Less strict length requirement
            assertTrue(tokenPart.isNotEmpty()) // Basic validation
        }
    }

    @Test
    fun `should generate tokens suitable for database storage`() {
        // When
        val tokens = (1..50).map { tokenGenerator.generateToken("db-test") }

        // Then
        tokens.forEach { token ->
            // Check that token doesn't contain characters that might cause DB issues
            assertFalse(token.contains("'"), "Token should not contain single quotes")
            assertFalse(token.contains("\""), "Token should not contain double quotes")
            assertFalse(token.contains(";"), "Token should not contain semicolons")
            assertFalse(token.contains("\\"), "Token should not contain backslashes")

            // Should be reasonable length for VARCHAR storage
            assertTrue(token.length <= 100, "Token should be under 100 characters for DB compatibility")
        }
    }

    // ========== Mock-based Tests ==========

    @Test
    fun `should use injected SecureRandom properly`() {
        // Given
        val customGenerator = DefaultAgentTokenGenerator()
        val field = DefaultAgentTokenGenerator::class.java.getDeclaredField("random")
        field.isAccessible = true
        field.set(customGenerator, mockRandom)

        val fixedBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17)
        whenever(mockRandom.nextBytes(any())).thenAnswer { invocation ->
            val array = invocation.getArgument<ByteArray>(0)
            System.arraycopy(fixedBytes, 0, array, 0, minOf(fixedBytes.size, array.size))
        }

        // When
        val token1 = customGenerator.generateToken("test")
        val token2 = customGenerator.generateToken("test")

        // Then
        assertEquals(token1, token2, "With mocked random, tokens should be identical")
        assertTrue(token1.startsWith("test-"))
    }
}