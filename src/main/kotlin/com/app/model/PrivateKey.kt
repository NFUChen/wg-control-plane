package com.app.model

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import java.util.*

/**
 * Simple private key entity for SSH authentication
 */
@Entity
@Table(name = "private_keys")
data class PrivateKey(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "name", nullable = false, unique = true)
    val name: String,

    @Column(name = "encrypted_private_key", nullable = false, columnDefinition = "TEXT")
    @JsonIgnore
    val encryptedPrivateKey: String,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    @Column(name = "description")
    val description: String? = null,

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
)
