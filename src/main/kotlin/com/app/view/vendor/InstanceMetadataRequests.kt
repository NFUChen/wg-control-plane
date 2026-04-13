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

/**
 * Request for upserting Azure VM instance metadata
 */
data class UpsertAzureInstanceMetadataRequest(
    val hostId: UUID,
    val vmId: String,
    val privateIpAddress: String,
    val location: String,
    val resourceGroupName: String,
    val subscriptionId: String,
    val networkInterfaceId: String,
    val macAddress: String,
) {

    /**
     * Validate the request data
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (vmId.isBlank()) {
            errors.add("vm_id cannot be blank")
        }

        if (privateIpAddress.isBlank()) {
            errors.add("private_ip_address cannot be blank")
        }

        if (location.isBlank()) {
            errors.add("location cannot be blank")
        }

        if (resourceGroupName.isBlank()) {
            errors.add("resource_group_name cannot be blank")
        }

        if (subscriptionId.isBlank()) {
            errors.add("subscription_id cannot be blank")
        }

        if (networkInterfaceId.isBlank()) {
            errors.add("network_interface_id cannot be blank")
        }

        return errors
    }
}