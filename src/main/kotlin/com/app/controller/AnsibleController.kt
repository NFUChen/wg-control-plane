package com.app.controller

import com.app.model.AnsibleHost
import com.app.model.AnsibleInventoryGroup
import com.app.service.ansible.AnsibleHostConnectivityService
import com.app.service.ansible.AnsibleManagementService
import com.app.service.ansible.InventoryFileInfo
import com.app.view.ansible.*
import com.app.view.ansible.toDetailResponse
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * REST controller for Ansible management operations.
 * Provides endpoints for managing hosts, groups, and inventory files.
 */
@RestController
@RequestMapping("/api/private/ansible")
class AnsibleController(
    private val ansibleManagementService: AnsibleManagementService,
    private val ansibleHostConnectivityService: AnsibleHostConnectivityService,
) {

    // ========== Host Management Endpoints ==========

    @GetMapping("/hosts")
    fun getAllHosts(@RequestParam(defaultValue = "false") enabledOnly: Boolean): ResponseEntity<List<AnsibleHost>> {
        val hosts = if (enabledOnly) {
            ansibleManagementService.getEnabledHosts()
        } else {
            ansibleManagementService.getAllHosts()
        }
        return ResponseEntity.ok(hosts)
    }

    @GetMapping("/hosts/{id}")
    fun getHost(@PathVariable id: UUID): ResponseEntity<AnsibleHost> {
        val host = ansibleManagementService.getHost(id)
        return if (host != null) {
            ResponseEntity.ok(host)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/hosts/by-hostname/{hostname}")
    fun getHostByHostname(@PathVariable hostname: String): ResponseEntity<AnsibleHost> {
        val host = ansibleManagementService.getHostByHostname(hostname)
        return ResponseEntity.ok(host)
    }

    @GetMapping("/hosts/ungrouped")
    fun getUngroupedHosts(): ResponseEntity<List<AnsibleHost>> {
        val hosts = ansibleManagementService.getUngroupedHosts()
        return ResponseEntity.ok(hosts)
    }

    @PostMapping("/hosts")
    fun createHost(@RequestBody request: CreateAnsibleHostRequest): ResponseEntity<AnsibleHost> {
        val createdHost = ansibleManagementService.createHost(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdHost)
    }

    @PutMapping("/hosts/{id}")
    fun updateHost(@PathVariable id: UUID, @RequestBody request: UpdateAnsibleHostRequest): ResponseEntity<AnsibleHost> {
        val updatedHost = ansibleManagementService.updateHost(id, request)
        return ResponseEntity.ok(updatedHost)
    }

    @DeleteMapping("/hosts/{id}")
    fun deleteHost(@PathVariable id: UUID): ResponseEntity<Void> {
        ansibleManagementService.deleteHost(id)
        return ResponseEntity.noContent().build()
    }

    /**
     * Runs [AnsibleHostConnectivityService.PING_PLAYBOOK] (ansible.builtin.ping) against this host synchronously.
     */
    @PostMapping("/hosts/{id}/health-check")
    fun healthCheckHost(@PathVariable id: UUID): ResponseEntity<AnsibleExecutionJobDetailResponse> {
        return try {
            val job = ansibleHostConnectivityService.runPing(id)
            ResponseEntity.ok(job.toDetailResponse())
        } catch (_: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    // ========== Group Management Endpoints ==========

    @GetMapping("/groups")
    fun getAllGroups(@RequestParam(defaultValue = "false") enabledOnly: Boolean): ResponseEntity<List<AnsibleInventoryGroup>> {
        val groups = if (enabledOnly) {
            ansibleManagementService.getEnabledGroups()
        } else {
            ansibleManagementService.getAllGroups()
        }
        return ResponseEntity.ok(groups)
    }

    @GetMapping("/groups/{id}")
    fun getGroup(@PathVariable id: UUID): ResponseEntity<AnsibleInventoryGroup> {
        val group = ansibleManagementService.getGroup(id)
        return ResponseEntity.ok(group)
    }

    @GetMapping("/groups/by-name/{name}")
    fun getGroupByName(@PathVariable name: String): ResponseEntity<AnsibleInventoryGroup> {
        val group = ansibleManagementService.getGroupByName(name)
        return ResponseEntity.ok(group)
    }

    @GetMapping("/groups/{id}/hosts")
    fun getHostsByGroup(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "false") enabledOnly: Boolean
    ): ResponseEntity<Any> {
        val group = ansibleManagementService.getGroup(id)
        val hosts = if (enabledOnly) {
            ansibleManagementService.getEnabledHostsByGroup(group)
        } else {
            ansibleManagementService.getHostsByGroup(group)
        }
        return ResponseEntity.ok(hosts)
    }

    @PostMapping("/groups")
    fun createGroup(@RequestBody request: CreateAnsibleInventoryGroupRequest): ResponseEntity<AnsibleInventoryGroup> {
        val createdGroup = ansibleManagementService.createGroup(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(createdGroup)
    }

    @PutMapping("/groups/{id}")
    fun updateGroup(@PathVariable id: UUID, @RequestBody request: UpdateAnsibleInventoryGroupRequest): ResponseEntity<AnsibleInventoryGroup> {
        val updatedGroup = ansibleManagementService.updateGroup(id, request)
        return ResponseEntity.ok(updatedGroup)
    }

    @DeleteMapping("/groups/{id}")
    fun deleteGroup(@PathVariable id: UUID): ResponseEntity<Void> {
        ansibleManagementService.deleteGroup(id)
        return ResponseEntity.noContent().build()
    }

    // ========== Inventory Management Endpoints ==========

    @GetMapping("/inventory/preview", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun previewInventory(): ResponseEntity<String> {
        val validationResult = ansibleManagementService.validateCurrentInventory()
        return if (validationResult.isValid) {
            ResponseEntity.ok()
                .header("X-Host-Count", validationResult.hostCount.toString())
                .header("X-Group-Count", validationResult.groupCount.toString())
                .body(validationResult.inventoryContent)
        } else {
            ResponseEntity.badRequest()
                .header("X-Validation-Errors", validationResult.errors.joinToString("; "))
                .body("# Inventory validation failed:\n# ${validationResult.errors.joinToString("\n# ")}")
        }
    }

    @PostMapping("/inventory/generate")
    fun generateInventory(
        @RequestParam(required = false) filename: String?,
        @RequestParam(defaultValue = "true") includeMetadata: Boolean
    ): ResponseEntity<InventoryFileInfo> {
        val fileInfo = ansibleManagementService.generateAndSaveInventory(filename, includeMetadata)
        return ResponseEntity.status(HttpStatus.CREATED).body(fileInfo)
    }

    @PostMapping("/inventory/generate/group/{groupId}")
    fun generateGroupInventory(
        @PathVariable groupId: UUID,
        @RequestParam(required = false) filename: String?,
        @RequestParam(defaultValue = "true") includeMetadata: Boolean
    ): ResponseEntity<InventoryFileInfo> {
        val fileInfo = ansibleManagementService.generateAndSaveGroupInventory(groupId, filename, includeMetadata)
        return ResponseEntity.status(HttpStatus.CREATED).body(fileInfo)
    }

    @GetMapping("/inventory/files")
    fun listInventoryFiles(): ResponseEntity<List<InventoryFileInfo>> {
        val files = ansibleManagementService.listInventoryFiles()
        return ResponseEntity.ok(files)
    }

    @GetMapping("/inventory/files/{filename}/content", produces = [MediaType.TEXT_PLAIN_VALUE])
    fun getInventoryFileContent(@PathVariable filename: String): ResponseEntity<String> {
        val content = ansibleManagementService.getInventoryFile(filename)
        return ResponseEntity.ok(content)
    }

    @GetMapping("/inventory/files/{filename}/download")
    fun downloadInventoryFile(@PathVariable filename: String): ResponseEntity<Resource> {
        val content = ansibleManagementService.getInventoryFile(filename)
        val resource = ByteArrayResource(content.toByteArray())

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .contentLength(resource.contentLength())
            .body(resource)
    }

    @DeleteMapping("/inventory/files/{filename}")
    fun deleteInventoryFile(@PathVariable filename: String): ResponseEntity<Void> {
        ansibleManagementService.deleteInventoryFile(filename)
        return ResponseEntity.noContent().build()
    }

    // ========== Validation and Statistics Endpoints ==========

    @GetMapping("/validation")
    fun validateInventory(): ResponseEntity<InventoryValidationResponse> {
        val validationResult = ansibleManagementService.validateCurrentInventory()
        val response = InventoryValidationResponse.fromValidationResult(validationResult)

        return if (validationResult.isValid) {
            ResponseEntity.ok(response)
        } else {
            ResponseEntity.badRequest().body(response)
        }
    }

    @GetMapping("/statistics")
    fun getStatistics(): ResponseEntity<AnsibleStatisticsResponse> {
        val stats = ansibleManagementService.getComprehensiveStatistics()
        val response = AnsibleStatisticsResponse.fromManagementStatistics(stats)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/maintenance")
    fun performMaintenance(
        @RequestParam(defaultValue = "30") deleteOlderThanDays: Int,
        @RequestParam(defaultValue = "true") validateInventory: Boolean
    ): ResponseEntity<MaintenanceResponse> {
        val result = ansibleManagementService.performMaintenance(deleteOlderThanDays, validateInventory)
        val response = MaintenanceResponse.fromMaintenanceResult(result)
        return ResponseEntity.ok(response)
    }
}