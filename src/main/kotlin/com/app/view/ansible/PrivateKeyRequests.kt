package com.app.view.ansible

import java.time.LocalDateTime
import java.util.*

/**
 * Private key metadata (no PEM content) for list/detail responses.
 */
data class PrivateKeySummaryResponse(
    val id: UUID,
    val name: String,
    val enabled: Boolean,
    val description: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class CreatePrivateKeyRequest(
    val name: String,
    val content: String,
    val description: String? = null,
    val enabled: Boolean = true
)

data class UpdatePrivateKeyRequest(
    val name: String,
    /** When null, existing key material is kept. */
    val content: String? = null,
    val description: String? = null,
    val enabled: Boolean = true
)
