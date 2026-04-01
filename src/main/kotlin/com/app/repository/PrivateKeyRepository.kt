package com.app.repository

import com.app.model.PrivateKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Simple repository for PrivateKey entities
 */
@Repository
interface PrivateKeyRepository : JpaRepository<PrivateKey, UUID> {

    /**
     * Find all enabled private keys
     */
    fun findByEnabledTrue(): List<PrivateKey>

    /**
     * Find private key by name
     */
    fun findByName(name: String): PrivateKey?

    /**
     * Check if private key with name exists
     */
    fun existsByName(name: String): Boolean
}