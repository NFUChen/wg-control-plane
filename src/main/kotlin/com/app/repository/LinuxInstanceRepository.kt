package com.app.repository

import com.app.model.LinuxInstance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository interface for LinuxInstance entities
 */
@Repository
interface LinuxInstanceRepository : JpaRepository<LinuxInstance, UUID> {

    /**
     * Find all enabled instances
     */
    fun findByEnabledTrue(): List<LinuxInstance>

    /**
     * Find instance by name
     */
    fun findByName(name: String): LinuxInstance?

    /**
     * Check if instance with name exists
     */
    fun existsByName(name: String): Boolean

    /**
     * Check if instance with IP address exists
     */
    fun existsByIpAddress(ipAddress: String): Boolean
}