package com.app.repository

import com.app.model.AnsibleInventoryGroup
import com.app.model.AnsibleHost
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository interface for AnsibleHost entities
 */
@Repository
interface AnsibleHostRepository : JpaRepository<AnsibleHost, UUID> {

    /**
     * Find all enabled instances
     */
    fun findByEnabledTrue(): List<AnsibleHost>

    /**
     * Find instance by hostname (inventory alias)
     */
    fun findByHostname(hostname: String): AnsibleHost?

    /**
     * Check if instance with hostname exists
     */
    fun existsByHostname(hostname: String): Boolean

    /**
     * Check if instance with IP address exists
     */
    fun existsByIpAddress(ipAddress: String): Boolean

    /**
     * Find instances by ansible inventory group
     */
    fun findByAnsibleInventoryGroup(ansibleInventoryGroup: AnsibleInventoryGroup): List<AnsibleHost>

    /**
     * Find enabled instances by ansible inventory group
     */
    fun findByEnabledTrueAndAnsibleInventoryGroup(ansibleInventoryGroup: AnsibleInventoryGroup): List<AnsibleHost>

    /**
     * Find instances without an ansible inventory group assigned
     */
    fun findByAnsibleInventoryGroupIsNull(): List<AnsibleHost>

    @Query("SELECT COUNT(h) FROM AnsibleHost h WHERE h.sshPrivateKey.id = :privateKeyId")
    fun countHostsUsingPrivateKey(@Param("privateKeyId") privateKeyId: UUID): Long
}