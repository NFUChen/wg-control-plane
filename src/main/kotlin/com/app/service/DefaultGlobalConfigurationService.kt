package com.app.service

import com.app.model.GlobalConfig
import com.app.model.GlobalConfiguration
import com.app.repository.GlobalConfigurationRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service("globalConfigurationService")
@Transactional
class DefaultGlobalConfigurationService(
    private val repository: com.app.repository.GlobalConfigurationRepository
) : GlobalConfigurationService {

    private val logger = LoggerFactory.getLogger(DefaultGlobalConfigurationService::class.java)

    /**
     * Initialize default configuration if none exists
     */
    @PostConstruct
    fun initializeDefaultConfiguration() {
        try {
            if (!repository.hasAnyConfiguration()) {
                logger.info("No global configuration found, creating default configuration")
                val defaultConfig = GlobalConfig()
                createConfiguration(
                    config = defaultConfig,
                    createdBy = "system",
                    changeDescription = "Initial default configuration"
                )
                logger.info("Default global configuration created")
            }
        } catch (e: Exception) {
            logger.warn("Could not initialize default configuration (this may be normal during first startup): ${e.message}")
            // This can happen during the first startup when the table doesn't exist yet
            // Hibernate will create the table and we can create the default config later
        }
    }

    override fun getCurrentConfiguration(): GlobalConfiguration {
        return try {
            repository.findCurrent()
                ?: createDefaultConfigurationIfMissing()
        } catch (e: Exception) {
            logger.warn("Could not find current configuration (table may not exist yet): ${e.message}")
            createDefaultConfigurationIfMissing()
        }
    }

    override fun getCurrentConfig(): GlobalConfig {
        return getCurrentConfiguration().config
    }

    override fun createConfiguration(
        config: GlobalConfig,
        createdBy: String?,
        changeDescription: String?
    ): GlobalConfiguration {
        // Validate configuration
        val validationErrors = config.validate()
        if (validationErrors.isNotEmpty()) {
            throw IllegalArgumentException("Configuration validation failed: ${validationErrors.joinToString(", ")}")
        }

        val nextVersion = getNextVersion()
        val configuration = GlobalConfiguration(
            version = nextVersion,
            config = config,
            createdBy = createdBy,
            changeDescription = changeDescription
        )

        val saved = repository.save(configuration)
        logger.info("Created new global configuration version: $nextVersion by $createdBy")

        return saved
    }

    override fun updateConfiguration(
        config: GlobalConfig,
        updatedBy: String?,
        changeDescription: String?
    ): GlobalConfiguration {
        // Always create a new configuration version instead of updating existing entity
        // This prevents detached instance issues since we never update existing entities
        return createConfiguration(config, updatedBy, changeDescription)
    }

    override fun getConfigurationByVersion(version: Long): GlobalConfiguration? {
        return repository.findByVersion(version)
    }

    override fun getConfigurationHistory(pageable: Pageable): Page<GlobalConfiguration> {
        return repository.findConfigurationHistory(pageable)
    }

    override fun getAllConfigurations(): List<GlobalConfiguration> {
        return repository.findAllOrderByVersionDesc()
    }

    override fun rollbackToVersion(targetVersion: Long, rolledBackBy: String?): GlobalConfiguration {
        val targetConfig = repository.findByVersion(targetVersion)
            ?: throw IllegalArgumentException("Configuration version $targetVersion not found")

        return createConfiguration(
            config = targetConfig.config.copy(),
            createdBy = rolledBackBy,
            changeDescription = "Rolled back to version $targetVersion"
        )
    }

    /**
     * Get next version number
     */
    private fun getNextVersion(): Long {
        val latestVersion = repository.getLatestVersion()
        return (latestVersion ?: 0) + 1
    }

    /**
     * Create default configuration when none exists (fallback)
     */
    private fun createDefaultConfigurationIfMissing(): GlobalConfiguration {
        logger.info("No global configuration found, creating default configuration as fallback")
        val defaultConfig = GlobalConfig()
        return try {
            createConfiguration(
                config = defaultConfig,
                createdBy = "system",
                changeDescription = "Default configuration created as fallback"
            )
        } catch (e: Exception) {
            logger.warn("Could not create default configuration in database, returning in-memory fallback: ${e.message}")
            // Return an in-memory fallback configuration
            GlobalConfiguration(
                version = 1,
                config = defaultConfig,
                createdBy = "system-fallback",
                changeDescription = "In-memory fallback configuration"
            )
        }
    }
}