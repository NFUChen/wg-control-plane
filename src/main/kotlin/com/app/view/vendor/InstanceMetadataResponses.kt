package com.app.view.vendor

import java.time.LocalDateTime
import java.util.*

/**
 * Response for instance metadata operations
 */
data class InstanceMetadataResponse(
    val id: UUID,
    val instanceId: String,
    val privateIp: String,
    val region: String,
    val availabilityZone: String,
    val networkInterfaceId: String,
    val vpcId: String,
    val ansibleHostId: UUID? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/**
 * Response for upsert operations indicating if a record was created or updated
 */
data class UpsertInstanceMetadataResponse(
    val metadata: InstanceMetadataResponse,
    val message: String
) {
}