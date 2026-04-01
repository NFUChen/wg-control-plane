package com.module.wgcontrolplane.service

import com.module.wgcontrolplane.model.GlobalConfig
import com.module.wgcontrolplane.model.GlobalConfiguration
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

/**
 * Service interface for managing global configuration with version control
 */
interface GlobalConfigurationService {

    /**
     * Get the current (latest) global configuration
     */
    fun getCurrentConfiguration(): GlobalConfiguration

    /**
     * Get current configuration data
     */
    fun getCurrentConfig(): GlobalConfig

    /**
     * Create a new configuration version
     */
    fun createConfiguration(
        config: GlobalConfig,
        createdBy: String? = null,
        changeDescription: String? = null
    ): GlobalConfiguration

    /**
     * Update configuration (creates new version)
     */
    fun updateConfiguration(
        config: GlobalConfig,
        updatedBy: String? = null,
        changeDescription: String? = null
    ): GlobalConfiguration

    /**
     * Get configuration by specific version
     */
    fun getConfigurationByVersion(version: Long): GlobalConfiguration?

    /**
     * Get configuration history with pagination
     */
    fun getConfigurationHistory(pageable: Pageable): Page<GlobalConfiguration>

    /**
     * Get all configurations ordered by version
     */
    fun getAllConfigurations(): List<GlobalConfiguration>

    /**
     * Rollback to a specific version
     */
    fun rollbackToVersion(
        targetVersion: Long,
        rolledBackBy: String? = null
    ): GlobalConfiguration
}