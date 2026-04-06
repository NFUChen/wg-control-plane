package com.app.service

import com.app.model.GlobalConfig
import com.app.model.GlobalConfiguration
import com.app.repository.GlobalConfigurationRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.test.context.ActiveProfiles
import java.util.*
import kotlin.test.*

/**
 * Comprehensive tests for DefaultGlobalConfigurationService
 * Tests configuration management, versioning, validation, and rollback functionality
 */
@ExtendWith(MockitoExtension::class)
@ActiveProfiles("test")
class DefaultGlobalConfigurationServiceTest {

    @Mock
    private lateinit var repository: GlobalConfigurationRepository

    private lateinit var configurationService: DefaultGlobalConfigurationService

    // Test data
    private lateinit var testConfig: GlobalConfig
    private lateinit var testConfiguration: GlobalConfiguration

    @BeforeEach
    fun setUp() {
        configurationService = DefaultGlobalConfigurationService(repository)

        // Setup test data
        testConfig = GlobalConfig(
            serverEndpoint = "vpn.example.com:51820",
            defaultDnsServers = listOf("8.8.8.8", "1.1.1.1"),
            defaultMtu = 1420,
            defaultPersistentKeepalive = 25
        )

        testConfiguration = GlobalConfiguration(
            id = UUID.randomUUID(),
            version = 1L,
            config = testConfig,
            createdBy = "test-user",
            changeDescription = "Test configuration"
        )
    }

    // ========== Initialization Tests ==========

    @Test
    fun `initializeDefaultConfiguration should create default config when none exists`() {
        // Given
        whenever(repository.hasAnyConfiguration()).thenReturn(false)
        whenever(repository.save(any<GlobalConfiguration>())).thenReturn(testConfiguration)
        whenever(repository.getLatestVersion()).thenReturn(null)

        // When
        configurationService.initializeDefaultConfiguration()

        // Then
        verify(repository).hasAnyConfiguration()
        verify(repository).save(any<GlobalConfiguration>())
    }

    @Test
    fun `initializeDefaultConfiguration should not create config when one exists`() {
        // Given
        whenever(repository.hasAnyConfiguration()).thenReturn(true)

        // When
        configurationService.initializeDefaultConfiguration()

        // Then
        verify(repository).hasAnyConfiguration()
        verify(repository, never()).save(any<GlobalConfiguration>())
    }

    @Test
    fun `initializeDefaultConfiguration should handle database errors gracefully`() {
        // Given
        whenever(repository.hasAnyConfiguration()).thenThrow(RuntimeException("Database not ready"))

        // When & Then - should not throw exception
        try {
            configurationService.initializeDefaultConfiguration()
            // Test passes if no exception is thrown
        } catch (e: Exception) {
            fail("Expected no exception but got: ${e.message}")
        }
    }

    // ========== Current Configuration Tests ==========

    @Test
    fun `getCurrentConfiguration should return current config from repository`() {
        // Given
        whenever(repository.findCurrent()).thenReturn(testConfiguration)

        // When
        val result = configurationService.getCurrentConfiguration()

        // Then
        assertEquals(testConfiguration, result)
        verify(repository).findCurrent()
    }

    @Test
    fun `getCurrentConfiguration should create default when none exists`() {
        // Given
        whenever(repository.findCurrent()).thenReturn(null)
        whenever(repository.getLatestVersion()).thenReturn(null)
        whenever(repository.save(any<GlobalConfiguration>())).thenReturn(testConfiguration)

        // When
        val result = configurationService.getCurrentConfiguration()

        // Then
        assertNotNull(result)
        verify(repository).findCurrent()
        verify(repository).save(any<GlobalConfiguration>())
    }

    @Test
    fun `getCurrentConfiguration should handle database errors with fallback`() {
        // Given
        whenever(repository.findCurrent()).thenThrow(RuntimeException("Database error"))
        whenever(repository.getLatestVersion()).thenThrow(RuntimeException("Database error"))

        // When
        val result = configurationService.getCurrentConfiguration()

        // Then
        assertNotNull(result)
        assertEquals("system-fallback", result.createdBy)
        assertEquals("In-memory fallback configuration", result.changeDescription)
    }

    @Test
    fun `getCurrentConfig should return config object from current configuration`() {
        // Given
        whenever(repository.findCurrent()).thenReturn(testConfiguration)

        // When
        val result = configurationService.getCurrentConfig()

        // Then
        assertEquals(testConfig, result)
    }

    // ========== Configuration Creation Tests ==========

    @Test
    fun `createConfiguration should create and save valid configuration`() {
        // Given
        val newConfig = testConfig.copy(defaultMtu = 1500)
        val savedConfiguration = testConfiguration.copy(version = 2L, config = newConfig)

        whenever(repository.getLatestVersion()).thenReturn(1L)
        whenever(repository.save(any<GlobalConfiguration>())).thenReturn(savedConfiguration)

        // When
        val result = configurationService.createConfiguration(
            config = newConfig,
            createdBy = "admin",
            changeDescription = "Updated MTU"
        )

        // Then
        assertEquals(savedConfiguration, result)
        verify(repository).getLatestVersion()
        verify(repository).save(any<GlobalConfiguration>())
    }

    @Test
    fun `createConfiguration should increment version correctly`() {
        // Given
        whenever(repository.getLatestVersion()).thenReturn(5L)
        whenever(repository.save(any<GlobalConfiguration>())).thenAnswer { invocation ->
            val config = invocation.getArgument<GlobalConfiguration>(0)
            assertEquals(6L, config.version)
            config
        }

        // When
        configurationService.createConfiguration(testConfig, "user", "Test")

        // Then
        verify(repository).save(any<GlobalConfiguration>())
    }

    @Test
    fun `createConfiguration should handle first version correctly`() {
        // Given
        whenever(repository.getLatestVersion()).thenReturn(null)
        whenever(repository.save(any<GlobalConfiguration>())).thenAnswer { invocation ->
            val config = invocation.getArgument<GlobalConfiguration>(0)
            assertEquals(1L, config.version)
            config
        }

        // When
        configurationService.createConfiguration(testConfig, "user", "First config")

        // Then
        verify(repository).save(any<GlobalConfiguration>())
    }

    @Test
    fun `createConfiguration should validate config before saving`() {
        // Given
        val invalidConfig = testConfig.copy(defaultMtu = 0) // Invalid MTU (below minimum of 1280)

        // When & Then - should throw exception due to validation failure
        val exception = assertThrows<IllegalArgumentException> {
            configurationService.createConfiguration(invalidConfig, "user", "Invalid config")
        }

        assertTrue(exception.message!!.contains("Configuration validation failed"))
        assertTrue(exception.message!!.contains("MTU"))
        // Verify that repository.save was never called due to validation failure
        verify(repository, never()).save(any<GlobalConfiguration>())
    }

    // ========== Update Configuration Tests ==========

    @Test
    fun `updateConfiguration should delegate to createConfiguration`() {
        // Given
        val updatedConfig = testConfig.copy(defaultPersistentKeepalive = 30)
        val newConfiguration = testConfiguration.copy(version = 3L, config = updatedConfig)

        whenever(repository.getLatestVersion()).thenReturn(2L)
        whenever(repository.save(any<GlobalConfiguration>())).thenReturn(newConfiguration)

        // When
        val result = configurationService.updateConfiguration(
            config = updatedConfig,
            updatedBy = "admin",
            changeDescription = "Updated keepalive"
        )

        // Then
        assertEquals(newConfiguration, result)
        assertEquals(3L, result.version)
        verify(repository).save(any<GlobalConfiguration>())
    }

    // ========== Version Management Tests ==========

    @Test
    fun `getConfigurationByVersion should return configuration for valid version`() {
        // Given
        val version = 5L
        whenever(repository.findByVersion(version)).thenReturn(testConfiguration)

        // When
        val result = configurationService.getConfigurationByVersion(version)

        // Then
        assertEquals(testConfiguration, result)
        verify(repository).findByVersion(version)
    }

    @Test
    fun `getConfigurationByVersion should return null for non-existent version`() {
        // Given
        val version = 999L
        whenever(repository.findByVersion(version)).thenReturn(null)

        // When
        val result = configurationService.getConfigurationByVersion(version)

        // Then
        assertNull(result)
        verify(repository).findByVersion(version)
    }

    // ========== History and Listing Tests ==========

    @Test
    fun `getConfigurationHistory should return paginated results`() {
        // Given
        val pageable = PageRequest.of(0, 10)
        val configList = listOf(testConfiguration)
        val page = PageImpl(configList, pageable, 1)

        whenever(repository.findConfigurationHistory(pageable)).thenReturn(page)

        // When
        val result = configurationService.getConfigurationHistory(pageable)

        // Then
        assertEquals(page, result)
        verify(repository).findConfigurationHistory(pageable)
    }

    @Test
    fun `getAllConfigurations should return all configurations`() {
        // Given
        val configList = listOf(
            testConfiguration,
            testConfiguration.copy(id = UUID.randomUUID(), version = 2L)
        )
        whenever(repository.findAllOrderByVersionDesc()).thenReturn(configList)

        // When
        val result = configurationService.getAllConfigurations()

        // Then
        assertEquals(configList, result)
        verify(repository).findAllOrderByVersionDesc()
    }

    // ========== Rollback Tests ==========

    @Test
    fun `rollbackToVersion should create new config with target version content`() {
        // Given
        val targetVersion = 3L
        val targetConfiguration = testConfiguration.copy(version = targetVersion)
        val newConfiguration = testConfiguration.copy(version = 5L)

        whenever(repository.findByVersion(targetVersion)).thenReturn(targetConfiguration)
        whenever(repository.getLatestVersion()).thenReturn(4L)
        whenever(repository.save(any<GlobalConfiguration>())).thenReturn(newConfiguration)

        // When
        val result = configurationService.rollbackToVersion(targetVersion, "admin")

        // Then
        assertEquals(newConfiguration, result)
        verify(repository).findByVersion(targetVersion)
        verify(repository).save(any<GlobalConfiguration>())
    }

    @Test
    fun `rollbackToVersion should throw exception for non-existent version`() {
        // Given
        val nonExistentVersion = 999L
        whenever(repository.findByVersion(nonExistentVersion)).thenReturn(null)

        // When & Then
        val exception = assertThrows<IllegalArgumentException> {
            configurationService.rollbackToVersion(nonExistentVersion, "admin")
        }

        assertTrue(exception.message!!.contains("not found"))
        verify(repository).findByVersion(nonExistentVersion)
        verify(repository, never()).save(any<GlobalConfiguration>())
    }

    @Test
    fun `rollbackToVersion should include proper change description`() {
        // Given
        val targetVersion = 2L
        val targetConfiguration = testConfiguration.copy(version = targetVersion)

        whenever(repository.findByVersion(targetVersion)).thenReturn(targetConfiguration)
        whenever(repository.getLatestVersion()).thenReturn(4L)
        whenever(repository.save(any<GlobalConfiguration>())).thenAnswer { invocation ->
            val config = invocation.getArgument<GlobalConfiguration>(0)
            assertEquals("Rolled back to version $targetVersion", config.changeDescription)
            config
        }

        // When
        configurationService.rollbackToVersion(targetVersion, "admin")

        // Then
        verify(repository).save(any<GlobalConfiguration>())
    }

    // ========== Private Method Tests (via public interface) ==========

    @Test
    fun `getNextVersion should work correctly when no configurations exist`() {
        // Given
        whenever(repository.getLatestVersion()).thenReturn(null)
        whenever(repository.save(any<GlobalConfiguration>())).thenAnswer { invocation ->
            val config = invocation.getArgument<GlobalConfiguration>(0)
            assertEquals(1L, config.version) // Should be 1 when no configs exist
            config
        }

        // When
        configurationService.createConfiguration(testConfig, "user", "First config")

        // Then
        verify(repository).save(any<GlobalConfiguration>())
    }

    // ========== Error Handling Tests ==========

    @Test
    fun `createDefaultConfigurationIfMissing should handle repository save failures`() {
        // Given - Simulate database not available
        whenever(repository.findCurrent()).thenReturn(null)
        whenever(repository.getLatestVersion()).thenThrow(RuntimeException("Database error"))

        // When
        val result = configurationService.getCurrentConfiguration()

        // Then - Should return in-memory fallback when database operations fail
        assertNotNull(result)
        assertEquals("system-fallback", result.createdBy)
        assertEquals("In-memory fallback configuration", result.changeDescription)
        verify(repository).findCurrent()
        verify(repository).getLatestVersion()
        // save() should never be called because getLatestVersion() throws exception first
        verify(repository, never()).save(any<GlobalConfiguration>())
    }

    // ========== Integration-style Tests ==========

    @Test
    fun `full configuration lifecycle should work correctly`() {
        // Given - Initial state
        whenever(repository.hasAnyConfiguration()).thenReturn(false)
        whenever(repository.getLatestVersion()).thenReturn(null, 1L, 2L)

        var savedConfigurations = mutableListOf<GlobalConfiguration>()
        whenever(repository.save(any<GlobalConfiguration>())).thenAnswer { invocation ->
            val config = invocation.getArgument<GlobalConfiguration>(0)
            savedConfigurations.add(config)
            config
        }

        // When - Initialize
        configurationService.initializeDefaultConfiguration()

        // When - Update
        val updatedConfig = testConfig.copy(defaultMtu = 1500)
        configurationService.updateConfiguration(updatedConfig, "admin", "Updated MTU")

        // Then
        assertEquals(2, savedConfigurations.size)
        assertEquals(1L, savedConfigurations[0].version)
        assertEquals(2L, savedConfigurations[1].version)
        assertEquals(1500, savedConfigurations[1].config.defaultMtu)
    }

    // ========== Helper Methods ==========
}