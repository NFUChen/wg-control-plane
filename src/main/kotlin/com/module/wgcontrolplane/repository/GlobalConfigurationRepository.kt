package com.module.wgcontrolplane.repository

import com.module.wgcontrolplane.model.GlobalConfiguration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface GlobalConfigurationRepository : JpaRepository<GlobalConfiguration, UUID> {

    /**
     * Get the current (latest version) global configuration
     */
    @Query("SELECT gc FROM GlobalConfiguration gc WHERE gc.version = (SELECT MAX(gc2.version) FROM GlobalConfiguration gc2)")
    fun findCurrent(): GlobalConfiguration?

    /**
     * Get configuration by specific version
     */
    @Query("SELECT gc FROM GlobalConfiguration gc WHERE gc.version = :version")
    fun findByVersion(@Param("version") version: Long): GlobalConfiguration?

    /**
     * Get the latest version number
     */
    @Query("SELECT MAX(gc.version) FROM GlobalConfiguration gc")
    fun getLatestVersion(): Long?

    /**
     * Get all configurations ordered by version (newest first)
     */
    @Query("SELECT gc FROM GlobalConfiguration gc ORDER BY gc.version DESC")
    fun findAllOrderByVersionDesc(): List<GlobalConfiguration>

    /**
     * Get configuration history with pagination
     */
    @Query("SELECT gc FROM GlobalConfiguration gc ORDER BY gc.version DESC")
    fun findConfigurationHistory(pageable: org.springframework.data.domain.Pageable): org.springframework.data.domain.Page<GlobalConfiguration>

    /**
     * Check if any configuration exists
     */
    @Query("SELECT COUNT(gc) > 0 FROM GlobalConfiguration gc")
    fun hasAnyConfiguration(): Boolean

    /**
     * Get configurations created by a specific user
     */
    @Query("SELECT gc FROM GlobalConfiguration gc WHERE gc.createdBy = :createdBy ORDER BY gc.version DESC")
    fun findByCreatedBy(@Param("createdBy") createdBy: String): List<GlobalConfiguration>

    /**
     * Get configurations created within a date range
     */
    @Query("SELECT gc FROM GlobalConfiguration gc WHERE gc.createdAt BETWEEN :startDate AND :endDate ORDER BY gc.version DESC")
    fun findByCreatedAtBetween(
        @Param("startDate") startDate: java.time.LocalDateTime,
        @Param("endDate") endDate: java.time.LocalDateTime
    ): List<GlobalConfiguration>

    /**
     * Delete old configurations keeping only the latest N versions
     */
    @Query("DELETE FROM GlobalConfiguration gc WHERE gc.version NOT IN (SELECT gc2.version FROM GlobalConfiguration gc2 ORDER BY gc2.version DESC LIMIT :keepCount)")
    fun deleteOldVersions(@Param("keepCount") keepCount: Int)
}