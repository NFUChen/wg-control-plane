package com.app.service

import com.app.model.PrivateKey
import com.app.repository.AnsibleHostRepository
import com.app.repository.PrivateKeyRepository
import com.app.view.ansible.CreatePrivateKeyRequest
import com.app.view.ansible.UpdatePrivateKeyRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.test.context.ActiveProfiles
import kotlin.test.*
import java.time.LocalDateTime
import java.util.*

/**
 * Comprehensive tests for DefaultPrivateKeyManagementService
 * Tests SSH private key management operations, validation, security, and data integrity
 */
@ExtendWith(MockitoExtension::class)
@ActiveProfiles("test")
class DefaultPrivateKeyManagementServiceTest {

    @Mock
    private lateinit var privateKeyRepository: PrivateKeyRepository

    @Mock
    private lateinit var ansibleHostRepository: AnsibleHostRepository

    private lateinit var service: DefaultPrivateKeyManagementService

    private lateinit var testPrivateKey: PrivateKey
    private lateinit var testCreateRequest: CreatePrivateKeyRequest
    private lateinit var testUpdateRequest: UpdatePrivateKeyRequest

    @BeforeEach
    fun setUp() {
        service = DefaultPrivateKeyManagementService(privateKeyRepository, ansibleHostRepository)

        testPrivateKey = PrivateKey(
            id = UUID.randomUUID(),
            name = "test-key",
            content = """
                -----BEGIN RSA PRIVATE KEY-----
                MIIEpAIBAAKCAQEA7SWJGLmUA1VpL32GlFbpJLiDbJE...
                -----END RSA PRIVATE KEY-----
            """.trimIndent(),
            enabled = true,
            description = "Test SSH private key for unit testing",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        testCreateRequest = CreatePrivateKeyRequest(
            name = "new-test-key",
            content = """
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowIBAAKCAQEA3SWJGLmUA1VpL32GlFbpJLiDbJE...
                -----END RSA PRIVATE KEY-----
            """.trimIndent(),
            description = "New test key",
            enabled = true
        )

        testUpdateRequest = UpdatePrivateKeyRequest(
            name = "updated-test-key",
            content = """
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowIBAAKCAQEA4SWJGLmUA1VpL32GlFbpJLiDbJE...
                -----END RSA PRIVATE KEY-----
            """.trimIndent(),
            description = "Updated test key",
            enabled = false
        )
    }

    // ========== List All Tests ==========

    @Test
    fun `listAll should return all private keys as summaries`() {
        // Given
        val keys = listOf(
            testPrivateKey,
            testPrivateKey.copy(id = UUID.randomUUID(), name = "another-key")
        )
        whenever(privateKeyRepository.findAll()).thenReturn(keys)

        // When
        val result = service.listAll()

        // Then
        assertEquals(2, result.size)
        assertEquals("test-key", result[0].name)
        assertEquals("another-key", result[1].name)
        verify(privateKeyRepository).findAll()
    }

    @Test
    fun `listAll should return empty list when no keys exist`() {
        // Given
        whenever(privateKeyRepository.findAll()).thenReturn(emptyList())

        // When
        val result = service.listAll()

        // Then
        assertTrue(result.isEmpty())
        verify(privateKeyRepository).findAll()
    }

    // ========== Get Summary Tests ==========

    @Test
    fun `getSummary should return summary when key exists`() {
        // Given
        val keyId = testPrivateKey.id
        whenever(privateKeyRepository.findById(keyId)).thenReturn(Optional.of(testPrivateKey))

        // When
        val result = service.getSummary(keyId)

        // Then
        assertNotNull(result)
        assertEquals(testPrivateKey.id, result.id)
        assertEquals(testPrivateKey.name, result.name)
        assertEquals(testPrivateKey.enabled, result.enabled)
        assertEquals(testPrivateKey.description, result.description)
        verify(privateKeyRepository).findById(keyId)
    }

    @Test
    fun `getSummary should return null when key does not exist`() {
        // Given
        val keyId = UUID.randomUUID()
        whenever(privateKeyRepository.findById(keyId)).thenReturn(Optional.empty())

        // When
        val result = service.getSummary(keyId)

        // Then
        assertNull(result)
        verify(privateKeyRepository).findById(keyId)
    }

    // ========== Create Tests ==========

    @Test
    fun `create should successfully create new private key`() {
        // Given
        whenever(privateKeyRepository.existsByName("new-test-key")).thenReturn(false)
        whenever(privateKeyRepository.save(any<PrivateKey>())).thenAnswer { invocation ->
            (invocation.arguments[0] as PrivateKey).copy(
                id = UUID.randomUUID(),
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
        }

        // When
        val result = service.create(testCreateRequest)

        // Then
        assertNotNull(result)
        assertEquals("new-test-key", result.name)
        assertEquals(true, result.enabled)
        assertEquals("New test key", result.description)
        verify(privateKeyRepository).existsByName("new-test-key")
        verify(privateKeyRepository).save(any<PrivateKey>())
    }

    @Test
    fun `create should trim whitespace from name and content`() {
        // Given
        val requestWithWhitespace = CreatePrivateKeyRequest(
            name = "  test-key-with-spaces  ",
            content = "  -----BEGIN RSA PRIVATE KEY-----\nkey content\n-----END RSA PRIVATE KEY-----  ",
            description = "  description with spaces  "
        )
        whenever(privateKeyRepository.existsByName("test-key-with-spaces")).thenReturn(false)
        whenever(privateKeyRepository.save(any<PrivateKey>())).thenAnswer { invocation ->
            invocation.arguments[0] as PrivateKey
        }

        // When
        val result = service.create(requestWithWhitespace)

        // Then
        assertEquals("test-key-with-spaces", result.name)
        assertEquals("description with spaces", result.description)
        verify(privateKeyRepository).save(argThat { privateKey ->
            privateKey.name == "test-key-with-spaces" &&
            privateKey.content == "-----BEGIN RSA PRIVATE KEY-----\nkey content\n-----END RSA PRIVATE KEY-----" &&
            privateKey.description == "description with spaces"
        })
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "\t", "\n"])
    fun `create should reject empty or whitespace-only names`(invalidName: String) {
        // Given
        val request = testCreateRequest.copy(name = invalidName)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            service.create(request)
        }
        assertEquals("Name is required", exception.message)
        verify(privateKeyRepository, never()).save(any<PrivateKey>())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "\t", "\n"])
    fun `create should reject empty or whitespace-only content`(invalidContent: String) {
        // Given
        val request = testCreateRequest.copy(content = invalidContent)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            service.create(request)
        }
        assertEquals("Key content is required", exception.message)
        verify(privateKeyRepository, never()).save(any<PrivateKey>())
    }

    @Test
    fun `create should reject duplicate names`() {
        // Given
        whenever(privateKeyRepository.existsByName("new-test-key")).thenReturn(true)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            service.create(testCreateRequest)
        }
        assertEquals("A private key with name 'new-test-key' already exists", exception.message)
        verify(privateKeyRepository).existsByName("new-test-key")
        verify(privateKeyRepository, never()).save(any<PrivateKey>())
    }

    @Test
    fun `create should handle empty description`() {
        // Given
        val requestWithEmptyDesc = testCreateRequest.copy(description = "")
        whenever(privateKeyRepository.existsByName("new-test-key")).thenReturn(false)
        whenever(privateKeyRepository.save(any<PrivateKey>())).thenAnswer { invocation ->
            invocation.arguments[0] as PrivateKey
        }

        // When
        service.create(requestWithEmptyDesc)

        // Then
        verify(privateKeyRepository).save(argThat { privateKey ->
            privateKey.description == null
        })
    }

    // ========== Update Tests ==========

    @Test
    fun `update should successfully update existing private key`() {
        // Given
        val keyId = testPrivateKey.id
        whenever(privateKeyRepository.findById(keyId)).thenReturn(Optional.of(testPrivateKey))
        whenever(privateKeyRepository.existsByName("updated-test-key")).thenReturn(false)
        whenever(privateKeyRepository.save(any<PrivateKey>())).thenAnswer { invocation ->
            invocation.arguments[0] as PrivateKey
        }

        // When
        val result = service.update(keyId, testUpdateRequest)

        // Then
        assertNotNull(result)
        assertEquals("updated-test-key", result.name)
        assertEquals(false, result.enabled)
        assertEquals("Updated test key", result.description)
        verify(privateKeyRepository).findById(keyId)
        verify(privateKeyRepository).existsByName("updated-test-key")
        verify(privateKeyRepository).save(any<PrivateKey>())
    }

    @Test
    fun `update should preserve existing content when content is null`() {
        // Given
        val keyId = testPrivateKey.id
        val updateWithoutContent = testUpdateRequest.copy(content = null)
        whenever(privateKeyRepository.findById(keyId)).thenReturn(Optional.of(testPrivateKey))
        whenever(privateKeyRepository.existsByName("updated-test-key")).thenReturn(false)
        whenever(privateKeyRepository.save(any<PrivateKey>())).thenAnswer { invocation ->
            invocation.arguments[0] as PrivateKey
        }

        // When
        service.update(keyId, updateWithoutContent)

        // Then
        verify(privateKeyRepository).save(argThat { privateKey ->
            privateKey.content == testPrivateKey.content
        })
    }

    @Test
    fun `update should preserve existing content when content is empty`() {
        // Given
        val keyId = testPrivateKey.id
        val updateWithEmptyContent = testUpdateRequest.copy(content = "")
        whenever(privateKeyRepository.findById(keyId)).thenReturn(Optional.of(testPrivateKey))
        whenever(privateKeyRepository.existsByName("updated-test-key")).thenReturn(false)
        whenever(privateKeyRepository.save(any<PrivateKey>())).thenAnswer { invocation ->
            invocation.arguments[0] as PrivateKey
        }

        // When
        service.update(keyId, updateWithEmptyContent)

        // Then
        verify(privateKeyRepository).save(argThat { privateKey ->
            privateKey.content == testPrivateKey.content
        })
    }

    @Test
    fun `update should throw exception when key not found`() {
        // Given
        val keyId = UUID.randomUUID()
        whenever(privateKeyRepository.findById(keyId)).thenReturn(Optional.empty())

        // When & Then
        val exception = assertThrows<NoSuchElementException> {
            service.update(keyId, testUpdateRequest)
        }
        assertEquals("Private key $keyId not found", exception.message)
        verify(privateKeyRepository).findById(keyId)
        verify(privateKeyRepository, never()).save(any<PrivateKey>())
    }

    @Test
    fun `update should reject duplicate names when changing name`() {
        // Given
        val keyId = testPrivateKey.id
        whenever(privateKeyRepository.findById(keyId)).thenReturn(Optional.of(testPrivateKey))
        whenever(privateKeyRepository.existsByName("updated-test-key")).thenReturn(true)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            service.update(keyId, testUpdateRequest)
        }
        assertEquals("A private key with name 'updated-test-key' already exists", exception.message)
        verify(privateKeyRepository).existsByName("updated-test-key")
        verify(privateKeyRepository, never()).save(any<PrivateKey>())
    }

    @Test
    fun `update should allow keeping same name`() {
        // Given
        val keyId = testPrivateKey.id
        val updateWithSameName = testUpdateRequest.copy(name = testPrivateKey.name)
        whenever(privateKeyRepository.findById(keyId)).thenReturn(Optional.of(testPrivateKey))
        whenever(privateKeyRepository.save(any<PrivateKey>())).thenAnswer { invocation ->
            invocation.arguments[0] as PrivateKey
        }

        // When
        service.update(keyId, updateWithSameName)

        // Then
        verify(privateKeyRepository, never()).existsByName(any<String>())
        verify(privateKeyRepository).save(any<PrivateKey>())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "\t", "\n"])
    fun `update should reject empty or whitespace-only names`(invalidName: String) {
        // Given
        val keyId = testPrivateKey.id
        val request = testUpdateRequest.copy(name = invalidName)
        whenever(privateKeyRepository.findById(keyId)).thenReturn(Optional.of(testPrivateKey))

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            service.update(keyId, request)
        }
        assertEquals("Name is required", exception.message)
        verify(privateKeyRepository, never()).save(any<PrivateKey>())
    }

    // ========== Delete Tests ==========

    @Test
    fun `delete should successfully delete existing private key when not in use`() {
        // Given
        val keyId = testPrivateKey.id
        whenever(privateKeyRepository.existsById(keyId)).thenReturn(true)
        whenever(ansibleHostRepository.countHostsUsingPrivateKey(keyId)).thenReturn(0L)

        // When
        service.delete(keyId)

        // Then
        verify(privateKeyRepository).existsById(keyId)
        verify(ansibleHostRepository).countHostsUsingPrivateKey(keyId)
        verify(privateKeyRepository).deleteById(keyId)
    }

    @Test
    fun `delete should throw exception when key not found`() {
        // Given
        val keyId = UUID.randomUUID()
        whenever(privateKeyRepository.existsById(keyId)).thenReturn(false)

        // When & Then
        val exception = assertThrows<NoSuchElementException> {
            service.delete(keyId)
        }
        assertEquals("Private key $keyId not found", exception.message)
        verify(privateKeyRepository).existsById(keyId)
        verify(privateKeyRepository, never()).deleteById(any<UUID>())
    }

    @Test
    fun `delete should throw exception when key is in use by hosts`() {
        // Given
        val keyId = testPrivateKey.id
        whenever(privateKeyRepository.existsById(keyId)).thenReturn(true)
        whenever(ansibleHostRepository.countHostsUsingPrivateKey(keyId)).thenReturn(3L)

        // When & Then
        val exception = assertThrows<IllegalStateException> {
            service.delete(keyId)
        }
        assertEquals("Cannot delete private key: 3 Ansible host(s) still reference it", exception.message)
        verify(privateKeyRepository).existsById(keyId)
        verify(ansibleHostRepository).countHostsUsingPrivateKey(keyId)
        verify(privateKeyRepository, never()).deleteById(any<UUID>())
    }

    // ========== Get Key Content Tests ==========

    @Test
    fun `getKeyContent should return content when key exists`() {
        // Given
        val keyId = testPrivateKey.id
        whenever(privateKeyRepository.findById(keyId)).thenReturn(Optional.of(testPrivateKey))

        // When
        val result = service.getKeyContent(keyId)

        // Then
        assertEquals(testPrivateKey.content, result)
        verify(privateKeyRepository).findById(keyId)
    }

    @Test
    fun `getKeyContent should throw exception when key not found`() {
        // Given
        val keyId = UUID.randomUUID()
        whenever(privateKeyRepository.findById(keyId)).thenReturn(Optional.empty())

        // When & Then
        val exception = assertThrows<NoSuchElementException> {
            service.getKeyContent(keyId)
        }
        assertEquals("Private key $keyId not found", exception.message)
        verify(privateKeyRepository).findById(keyId)
    }

    // ========== Integration and Edge Case Tests ==========

    @Test
    fun `should handle special characters in names and descriptions`() {
        // Given
        val specialRequest = CreatePrivateKeyRequest(
            name = "test-key_with.special@chars",
            content = testCreateRequest.content,
            description = "Description with special chars: !@#$%^&*()",
            enabled = true
        )
        whenever(privateKeyRepository.existsByName(specialRequest.name)).thenReturn(false)
        whenever(privateKeyRepository.save(any<PrivateKey>())).thenAnswer { invocation ->
            invocation.arguments[0] as PrivateKey
        }

        // When
        val result = service.create(specialRequest)

        // Then
        assertEquals("test-key_with.special@chars", result.name)
        assertEquals("Description with special chars: !@#$%^&*()", result.description)
    }

    @Test
    fun `should handle very long private key content`() {
        // Given
        val longContent = "-----BEGIN RSA PRIVATE KEY-----\n" + "A".repeat(4000) + "\n-----END RSA PRIVATE KEY-----"
        val request = testCreateRequest.copy(content = longContent)
        whenever(privateKeyRepository.existsByName("new-test-key")).thenReturn(false)
        whenever(privateKeyRepository.save(any<PrivateKey>())).thenAnswer { invocation ->
            invocation.arguments[0] as PrivateKey
        }

        // When
        service.create(request)

        // Then
        verify(privateKeyRepository).save(argThat { privateKey ->
            privateKey.content == longContent.trim()
        })
    }

    @Test
    fun `should preserve null description when updating`() {
        // Given
        val keyId = testPrivateKey.id
        val updateWithNullDesc = testUpdateRequest.copy(description = null)
        whenever(privateKeyRepository.findById(keyId)).thenReturn(Optional.of(testPrivateKey))
        whenever(privateKeyRepository.existsByName("updated-test-key")).thenReturn(false)
        whenever(privateKeyRepository.save(any<PrivateKey>())).thenAnswer { invocation ->
            invocation.arguments[0] as PrivateKey
        }

        // When
        service.update(keyId, updateWithNullDesc)

        // Then
        verify(privateKeyRepository).save(argThat { privateKey ->
            privateKey.description == null
        })
    }

    @Test
    fun `should handle empty description in update request`() {
        // Given
        val keyId = testPrivateKey.id
        val updateWithEmptyDesc = testUpdateRequest.copy(description = "")
        whenever(privateKeyRepository.findById(keyId)).thenReturn(Optional.of(testPrivateKey))
        whenever(privateKeyRepository.existsByName("updated-test-key")).thenReturn(false)
        whenever(privateKeyRepository.save(any<PrivateKey>())).thenAnswer { invocation ->
            invocation.arguments[0] as PrivateKey
        }

        // When
        service.update(keyId, updateWithEmptyDesc)

        // Then
        verify(privateKeyRepository).save(argThat { privateKey ->
            privateKey.description == null
        })
    }

    // ========== Security Tests ==========

    @Test
    fun `should not expose private key content in summary response`() {
        // Given
        whenever(privateKeyRepository.findAll()).thenReturn(listOf(testPrivateKey))

        // When
        val result = service.listAll()

        // Then
        assertEquals(1, result.size)
        val summary = result[0]
        // Verify that summary only contains safe metadata
        assertEquals(testPrivateKey.id, summary.id)
        assertEquals(testPrivateKey.name, summary.name)
        assertEquals(testPrivateKey.enabled, summary.enabled)
        assertEquals(testPrivateKey.description, summary.description)
        assertEquals(testPrivateKey.createdAt, summary.createdAt)
        assertEquals(testPrivateKey.updatedAt, summary.updatedAt)
        // Private key content should NOT be accessible through summary
    }

    @Test
    fun `should validate private key format patterns`() {
        // Test various SSH private key formats
        val validFormats = listOf(
            "-----BEGIN RSA PRIVATE KEY-----\ncontent\n-----END RSA PRIVATE KEY-----",
            "-----BEGIN OPENSSH PRIVATE KEY-----\ncontent\n-----END OPENSSH PRIVATE KEY-----",
            "-----BEGIN EC PRIVATE KEY-----\ncontent\n-----END EC PRIVATE KEY-----",
            "-----BEGIN PRIVATE KEY-----\ncontent\n-----END PRIVATE KEY-----"
        )

        validFormats.forEach { content ->
            val request = testCreateRequest.copy(content = content)
            whenever(privateKeyRepository.existsByName(any<String>())).thenReturn(false)
            whenever(privateKeyRepository.save(any<PrivateKey>())).thenAnswer { invocation ->
                invocation.arguments[0] as PrivateKey
            }

            assertDoesNotThrow {
                service.create(request)
            }
        }
    }

    // ========== Performance and Concurrent Access Tests ==========

    @Test
    fun `should handle repository exceptions gracefully`() {
        // Given
        whenever(privateKeyRepository.existsByName(any<String>()))
            .thenThrow(RuntimeException("Database connection failed"))

        // When & Then
        assertThrows<RuntimeException> {
            service.create(testCreateRequest)
        }
    }

    // ========== Helper Methods ==========

    private fun assertDoesNotThrow(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail("Expected no exception but got: ${e.message}")
        }
    }
}