package com.app.view.vendor

import java.util.*

/**
 * Request for upserting AWS EC2 instance metadata
 */
data class UpsertInstanceMetadataRequest(
    val hostId: UUID,
    val instanceId: String,
    val privateIp: String,
    val region: String,
    val availabilityZone: String,
    val networkInterfaceId: String,
    val vpcId: String,
) {

    /**
     * Validate the request data
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (instanceId.isBlank()) {
            errors.add("instance_id cannot be blank")
        }

        if (privateIp.isBlank()) {
            errors.add("private_ip cannot be blank")
        }

        if (region.isBlank()) {
            errors.add("region cannot be blank")
        }

        if (availabilityZone.isBlank()) {
            errors.add("availability_zone cannot be blank")
        }

        if (networkInterfaceId.isBlank()) {
            errors.add("network_interface_id cannot be blank")
        }

        if (vpcId.isBlank()) {
            errors.add("vpc_id cannot be blank")
        }

        return errors
    }
}