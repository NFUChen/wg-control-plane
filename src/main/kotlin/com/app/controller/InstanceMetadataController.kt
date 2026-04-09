package com.app.controller

import com.app.service.vendor.AwsInstanceMetadataService
import com.app.view.vendor.UpsertInstanceMetadataRequest
import com.app.view.vendor.UpsertInstanceMetadataResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller for instance metadata operations.
 * Provides endpoints for Ansible playbooks to upsert instance metadata.
 */
@RestController
@RequestMapping("/api/public/instance-metadata")
class InstanceMetadataController(
    private val awsInstanceMetadataService: AwsInstanceMetadataService
) {

    private val logger = LoggerFactory.getLogger(InstanceMetadataController::class.java)

    /**
     * Upsert instance metadata - main endpoint for Ansible playbooks
     */
    @PostMapping("/aws/upsert")
    fun upsertInstanceMetadata(@RequestBody request: UpsertInstanceMetadataRequest): ResponseEntity<UpsertInstanceMetadataResponse> {
        logger.info("Received upsert request for instance: ${request.instanceId}")
        val response = awsInstanceMetadataService.upsertInstanceMetadata(request)
        return ResponseEntity.ok(response)
    }
    /**
     * Delete instance metadata
     */
    @DeleteMapping("/instance/{instanceId}")
    fun deleteInstanceMetadata(@PathVariable instanceId: String): ResponseEntity<Map<String, String>> {
        val deleted = awsInstanceMetadataService.deleteInstanceMetadata(instanceId)
        return if (deleted) {
            ResponseEntity.ok(mapOf("message" to "Instance metadata deleted successfully"))
        } else {
            ResponseEntity.notFound().build()
        }
    }
}