package com.app.view.vendor

import java.time.LocalDateTime
import java.util.*

/**
 * Response for instance metadata operations
 */
data class AwsInstanceMetadataResponse(
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
data class UpsertAwsInstanceMetadataResponse(
    val metadata: AwsInstanceMetadataResponse,
    val message: String
) {
}

/**
 * Response for Azure instance metadata operations
 */
data class AzureInstanceMetadataResponse(
    val id: UUID,
    val vmId: String,
    val privateIpAddress: String,
    val location: String,
    val resourceGroupName: String,
    val subscriptionId: String,
    val networkInterfaceId: String,
    val macAddress: String,
    val ansibleHostId: UUID? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

/**
 * Response for Azure upsert operations indicating if a record was created or updated
 */
data class UpsertAzureInstanceMetadataResponse(
    val metadata: AzureInstanceMetadataResponse,
    val message: String
)