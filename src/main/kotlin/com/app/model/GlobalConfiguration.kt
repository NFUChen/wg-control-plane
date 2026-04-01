package com.app.model

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.*

/**
 * Global configuration with version control
 * Each configuration change creates a new row with incremented version
 */
@Entity
@Table(name = "global_configuration")
data class GlobalConfiguration(
    @Id
    val id: UUID = UUID.randomUUID(),

    /**
     * Configuration version (auto-increment)
     * Always query for MAX(version) to get current config
     */
    @Column(name = "version", nullable = false, unique = true)
    val version: Long,

    /**
     * Complete global configuration as JSON
     * Made immutable to prevent detached instance issues
     */
    @Column(name = "config", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    val config: GlobalConfig,

    /**
     * Who made this configuration change
     * Made immutable to prevent detached instance issues
     */
    @Column(name = "created_by")
    val createdBy: String? = null,

    /**
     * Optional change description
     * Made immutable to prevent detached instance issues
     */
    @Column(name = "change_description")
    val changeDescription: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    /**
     * Optimistic locking version for handling concurrent updates
     * This helps prevent "updated by another transaction" errors
     */
    @Version
    @Column(name = "entity_version")
    val entityVersion: Long = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GlobalConfiguration
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String {
        return "GlobalConfiguration(id=$id, version=$version, createdAt=$createdAt)"
    }
}

/**
 * Global configuration data structure
 * This will be serialized to JSON in the database
 */
data class GlobalConfig(
    /**
     * Public endpoint for WireGuard server (replaces WireGuardServer.endpoint)
     */
    val serverEndpoint: String = "",

    /**
     * Default DNS servers for new clients
     */
    val defaultDnsServers: List<String> = listOf("8.8.8.8", "1.1.1.1"),

    /**
     * Default MTU size for new configurations
     */
    val defaultMtu: Int = 1420,

    /**
     * Default persistent keepalive interval
     */
    val defaultPersistentKeepalive: Int = 25,

    /**
     * Security settings
     */
    val enablePresharedKeys: Boolean = true,
    val autoGenerateKeys: Boolean = true
    
) {
    /**
     * Validate configuration settings
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (defaultDnsServers.isEmpty()) {
            errors.add("At least one DNS server must be configured")
        }

        if (defaultMtu < 1280 || defaultMtu > 65536) {
            errors.add("MTU must be between 1280 and 65536")
        }

        if (defaultPersistentKeepalive < 0 || defaultPersistentKeepalive > 3600) {
            errors.add("Persistent keepalive must be between 0 and 3600 seconds")
        }

        return errors
    }
}