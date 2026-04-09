package com.app.service.vendor

import com.app.model.AnsibleHost
import com.app.model.vendor.AwsEc2InstanceMetadata
import com.app.repository.AnsibleHostRepository
import com.app.repository.AwsEc2InstanceMetadataRepository
import com.app.view.vendor.AwsInstanceMetadataResponse
import com.app.view.vendor.UpsertInstanceMetadataRequest
import com.app.view.vendor.UpsertAwsInstanceMetadataResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Service for managing cloud instance metadata collected by Ansible playbooks
 */
@Service
@Transactional
class AwsInstanceMetadataService(
    private val ansibleHostRepository: AnsibleHostRepository,
    private val instanceRepository: AwsEc2InstanceMetadataRepository
) {

    private val logger = LoggerFactory.getLogger(AwsInstanceMetadataService::class.java)

    /**
     * Upsert instance metadata from Ansible playbook
     * Creates new record if instance ID doesn't exist, updates existing record otherwise
     */
    fun upsertInstanceMetadata(request: UpsertInstanceMetadataRequest): UpsertAwsInstanceMetadataResponse {
        // Validate request
        val validationErrors = request.validate()
        if (validationErrors.isNotEmpty()) {
            throw IllegalArgumentException("Validation failed: ${validationErrors.joinToString(", ")}")
        }

        val ansibleHost = ansibleHostRepository.findById(request.hostId).orElseThrow {
            IllegalArgumentException("Ansible host with ID ${request.hostId} not found")
        }

        logger.info("Upserting instance metadata for instance: ${request.instanceId}")

        // Check if instance metadata already exists
        val existing = instanceRepository.findByInstanceId(request.instanceId)

        return if (existing != null) {
            // Update existing metadata
            val updated = updateExistingMetadata(existing, request)
            logger.info("Updated instance metadata for instance: ${request.instanceId}")
            createUpsertResponse(updated, "Instance metadata updated successfully")
        } else {
            // Create new metadata
            // if there exists a primary instance in the same VPC, new instance will be created as non-primary to avoid unique constraint violation
            var hasPrimary = false
            instanceRepository.countByVpcIdAndIsPrimaryTrue(request.vpcId).let { primaryCount ->
                if (primaryCount > 0) {
                    hasPrimary = true
                    logger.warn("Primary instance already exists in VPC ${request.vpcId}. New instance will be created as non-primary.")
                }
            }
            val created = createNewMetadata(request, ansibleHost, !hasPrimary)
            logger.info("Created new instance metadata for instance: ${request.instanceId}")
            createUpsertResponse(created, "Instance metadata created successfully")
        }
    }

    /**
     * Delete instance metadata by instance ID
     */
    fun deleteInstanceMetadata(instanceId: String): Boolean {
        val existing = instanceRepository.findByInstanceId(instanceId)
        return if (existing != null) {
            instanceRepository.delete(existing)
            logger.info("Deleted instance metadata for instance: $instanceId")
            true
        } else {
            false
        }
    }

    /**
     * Create new instance metadata from request
     */
    private fun createNewMetadata(request: UpsertInstanceMetadataRequest, ansibleHost: AnsibleHost, isPrimary: Boolean): AwsEc2InstanceMetadata {
        val metadata = AwsEc2InstanceMetadata(
            instanceId = request.instanceId.trim(),
            privateIp = request.privateIp.trim(),
            region = request.region.trim(),
            availabilityZone = request.availabilityZone.trim(),
            networkInterfaceId = request.networkInterfaceId.trim(),
            vpcId = request.vpcId.trim(),
            ansibleHost = ansibleHost,
            isPrimary = isPrimary
        )

        return instanceRepository.save(metadata)
    }

    /**
     * Update existing instance metadata from request
     */
    private fun updateExistingMetadata(
        existing: AwsEc2InstanceMetadata,
        request: UpsertInstanceMetadataRequest
    ): AwsEc2InstanceMetadata {
        // Create updated instance (since we use data class, we need to create a new instance)
        val updated = existing.copy(
            privateIp = request.privateIp.trim(),
            region = request.region.trim(),
            availabilityZone = request.availabilityZone.trim(),
            networkInterfaceId = request.networkInterfaceId.trim(),
            vpcId = request.vpcId.trim(),
            updatedAt = LocalDateTime.now()
        )

        // Delete the old record and save the new one (due to data class immutability)
        instanceRepository.delete(existing)
        return instanceRepository.save(updated)
    }

    /**
     * Check if instance metadata exists for given instance ID
     */
    fun existsByInstanceId(instanceId: String): Boolean {
        return instanceRepository.existsByInstanceId(instanceId)
    }

    /**
     * Create UpsertInstanceMetadataResponse from entity and message
     */
    private fun createUpsertResponse(metadata: AwsEc2InstanceMetadata, message: String): UpsertAwsInstanceMetadataResponse {
        return UpsertAwsInstanceMetadataResponse(
            metadata = AwsInstanceMetadataResponse(
                id = metadata.id,
                instanceId = metadata.instanceId,
                privateIp = metadata.privateIp,
                region = metadata.region,
                availabilityZone = metadata.availabilityZone,
                networkInterfaceId = metadata.networkInterfaceId,
                vpcId = metadata.vpcId,
                ansibleHostId = metadata.ansibleHost?.id,
                createdAt = metadata.createdAt,
                updatedAt = metadata.updatedAt
            ),
            message = message
        )
    }
}












