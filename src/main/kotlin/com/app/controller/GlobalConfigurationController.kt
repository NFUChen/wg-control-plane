package com.app.controller

import com.app.model.GlobalConfig
import com.app.model.GlobalConfiguration
import com.app.service.GlobalConfigurationService
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/global-config")
class GlobalConfigurationController(
    private val configurationService: GlobalConfigurationService
) {

    /**
     * Get current global configuration
     */
    @GetMapping("/current")
    fun getCurrentConfiguration(): ResponseEntity<GlobalConfiguration> {
        val config = configurationService.getCurrentConfiguration()
        return ResponseEntity.ok(config)
    }

    /**
     * Get current configuration data only (without metadata)
     */
    @GetMapping("/current/data")
    fun getCurrentConfigurationData(): ResponseEntity<GlobalConfig> {
        val config = configurationService.getCurrentConfig()
        return ResponseEntity.ok(config)
    }

    /**
     * Update global configuration (creates new version)
     */
    @PutMapping("/update")
    fun updateConfiguration(
        @RequestBody config: GlobalConfig,
        @RequestParam(required = false) updatedBy: String? = null,
        @RequestParam(required = false) changeDescription: String? = null
    ): ResponseEntity<GlobalConfiguration> {
        try {
            val updated = configurationService.updateConfiguration(config, updatedBy, changeDescription)
            return ResponseEntity.ok(updated)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
    }


    /**
     * Get configuration by specific version
     */
    @GetMapping("/version/{version}")
    fun getConfigurationByVersion(@PathVariable version: Long): ResponseEntity<GlobalConfiguration> {
        val config = configurationService.getConfigurationByVersion(version)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Configuration version $version not found")
        return ResponseEntity.ok(config)
    }

    /**
     * Get configuration history with pagination
     */
    @GetMapping("/history")
    fun getConfigurationHistory(
        @PageableDefault(size = 20, sort = ["version"]) pageable: Pageable
    ): ResponseEntity<Page<GlobalConfiguration>> {
        val history = configurationService.getConfigurationHistory(pageable)
        return ResponseEntity.ok(history)
    }

    /**
     * Get all configurations
     */
    @GetMapping("/all")
    fun getAllConfigurations(): ResponseEntity<List<GlobalConfiguration>> {
        val configs = configurationService.getAllConfigurations()
        return ResponseEntity.ok(configs)
    }

    /**
     * Rollback to a specific version
     */
    @PostMapping("/rollback/{version}")
    fun rollbackToVersion(
        @PathVariable version: Long,
        @RequestParam(required = false) rolledBackBy: String? = null
    ): ResponseEntity<GlobalConfiguration> {
        try {
            val config = configurationService.rollbackToVersion(version, rolledBackBy)
            return ResponseEntity.ok(config)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
    }
}