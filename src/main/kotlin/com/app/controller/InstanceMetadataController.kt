package com.app.controller

import com.app.service.vendor.AwsInstanceMetadataService
import com.app.view.vendor.UpsertAzureInstanceMetadataRequest
import com.app.view.vendor.UpsertInstanceMetadataRequest
import com.fasterxml.jackson.databind.ObjectMapper
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
    private val awsInstanceMetadataService: AwsInstanceMetadataService,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(InstanceMetadataController::class.java)

    /**
     * Generic upsert instance metadata based on provider
     */
    @PostMapping("/upsert")
    fun upsertInstanceMetadataGeneric(
        @RequestParam provider: String,
        @RequestBody request: Any
    ): ResponseEntity<Any> {
        return try {
            when (provider.lowercase()) {
                "aws" -> {
                    logger.info("Processing AWS metadata upsert")
                    val awsRequest = objectMapper.convertValue(request, UpsertInstanceMetadataRequest::class.java)
                    val response = awsInstanceMetadataService.upsertInstanceMetadata(awsRequest)
                    ResponseEntity.ok(response)
                }
                "azure" -> {
                    logger.info("Processing Azure metadata upsert")
                    val azureRequest = objectMapper.convertValue(request, UpsertAzureInstanceMetadataRequest::class.java)
                    // TODO: Implement Azure service or handle Azure metadata here
                    ResponseEntity.ok(mapOf("message" to "Azure metadata received", "data" to azureRequest))
                }
                else -> {
                    logger.warn("Unsupported provider: $provider")
                    ResponseEntity.badRequest().body(mapOf("error" to "Unsupported provider: $provider"))
                }
            }
        } catch (e: Exception) {
            logger.error("Error converting request for provider $provider", e)
            ResponseEntity.badRequest().body(mapOf("error" to "Invalid request format: ${e.message}"))
        }
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