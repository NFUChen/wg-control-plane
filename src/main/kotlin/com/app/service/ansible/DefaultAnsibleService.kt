package com.app.service.ansible

import com.app.model.AnsibleHost
import com.app.model.AnsibleInventoryGroup
import com.app.model.IPAddress
import com.app.repository.AnsibleHostRepository
import com.app.repository.AnsibleInventoryGroupRepository
import com.app.repository.PrivateKeyRepository
import com.app.view.ansible.CreateAnsibleHostRequest
import com.app.view.ansible.CreateAnsibleInventoryGroupRequest
import com.app.view.ansible.UpdateAnsibleHostRequest
import com.app.view.ansible.UpdateAnsibleInventoryGroupRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.jvm.optionals.getOrNull

/**
 * Default implementation of AnsibleService.
 * Provides business logic layer for Ansible-related operations.
 */
@Service
@Transactional
class DefaultAnsibleService(
    private val ansibleHostRepository: AnsibleHostRepository,
    private val ansibleInventoryGroupRepository: AnsibleInventoryGroupRepository,
    private val privateKeyRepository: PrivateKeyRepository,
    private val ansibleInventoryGenerator: AnsibleInventoryGenerator,
    private val objectMapper: ObjectMapper
) : AnsibleService {

    // ========== Host Management ==========

    override fun createHost(request: CreateAnsibleHostRequest): AnsibleHost {
        // Validate the request
        val validationErrors = validateCreateHostRequest(request)
        if (validationErrors.isNotEmpty()) {
            throw IllegalArgumentException("Host validation failed: ${validationErrors.joinToString(", ")}")
        }

        // Check for duplicate names and IPs
        if (ansibleHostRepository.existsByName(request.name)) {
            throw IllegalArgumentException("Host with name '${request.name}' already exists")
        }

        if (ansibleHostRepository.existsByIpAddress(request.ipAddress)) {
            throw IllegalArgumentException("Host with IP address '${request.ipAddress}' already exists")
        }

        // Lookup private key
        val privateKey = privateKeyRepository.findById(request.sshPrivateKeyId).orElse(null)
            ?: throw IllegalArgumentException("Private key with ID ${request.sshPrivateKeyId} not found")

        // Lookup inventory group if specified
        val inventoryGroup = request.ansibleInventoryGroupId?.let { groupId ->
            ansibleInventoryGroupRepository.findById(groupId).orElse(null)
                ?: throw IllegalArgumentException("Inventory group with ID $groupId not found")
        }

        // Convert request to entity
        val host = AnsibleHost(
            name = request.name.trim(),
            hostname = request.hostname.trim(),
            ipAddress = request.ipAddress.trim(),
            sshPort = request.sshPort,
            sshUsername = request.sshUsername.trim(),
            sshPrivateKey = privateKey,
            ansibleInventoryGroup = inventoryGroup,
            sudoRequired = request.sudoRequired,
            sudoPassword = request.sudoPassword?.trim(),
            pythonInterpreter = request.pythonInterpreter?.trim(),
            enabled = request.enabled,
            tags = request.tags?.let { objectMapper.writeValueAsString(it) },
            description = request.description?.trim(),
            annotation = buildCustomVariablesAnnotation(request.customVariables)
        )

        return ansibleHostRepository.save(host)
    }

    override fun updateHost(id: UUID, request: UpdateAnsibleHostRequest): AnsibleHost {
        val existingHost = ansibleHostRepository.findById(id).orElse(null)
            ?: throw NoSuchElementException("Host with ID $id not found")

        // Validate the request
        val validationErrors = validateUpdateHostRequest(request)
        if (validationErrors.isNotEmpty()) {
            throw IllegalArgumentException("Host validation failed: ${validationErrors.joinToString(", ")}")
        }

        // Check for duplicate names and IPs (excluding current host)
        if (request.name != existingHost.name && ansibleHostRepository.existsByName(request.name)) {
            throw IllegalArgumentException("Host with name '${request.name}' already exists")
        }

        if (request.ipAddress != existingHost.ipAddress && ansibleHostRepository.existsByIpAddress(request.ipAddress)) {
            throw IllegalArgumentException("Host with IP address '${request.ipAddress}' already exists")
        }

        // Lookup private key
        val privateKey = privateKeyRepository.findById(request.sshPrivateKeyId).orElse(null)
            ?: throw IllegalArgumentException("Private key with ID ${request.sshPrivateKeyId} not found")

        // Lookup inventory group if specified
        val inventoryGroup = request.ansibleInventoryGroupId?.let { groupId ->
            ansibleInventoryGroupRepository.findById(groupId).orElse(null)
                ?: throw IllegalArgumentException("Inventory group with ID $groupId not found")
        }

        // Convert request to entity, preserving ID and creation time
        val hostToUpdate = existingHost.copy(
            name = request.name.trim(),
            hostname = request.hostname.trim(),
            ipAddress = request.ipAddress.trim(),
            sshPort = request.sshPort,
            sshUsername = request.sshUsername.trim(),
            sshPrivateKey = privateKey,
            ansibleInventoryGroup = inventoryGroup,
            sudoRequired = request.sudoRequired,
            sudoPassword = request.sudoPassword?.trim(),
            pythonInterpreter = request.pythonInterpreter?.trim(),
            enabled = request.enabled,
            tags = request.tags?.let { objectMapper.writeValueAsString(it) },
            description = request.description?.trim(),
            annotation = buildCustomVariablesAnnotation(request.customVariables)
        )

        return ansibleHostRepository.save(hostToUpdate)
    }

    override fun getHost(id: UUID): AnsibleHost {
        return ansibleHostRepository.findById(id).getOrNull() ?: throw IllegalArgumentException("Host with ID $id not found")
    }

    override fun getHostByName(name: String): AnsibleHost {
        return ansibleHostRepository.findByName(name) ?: throw IllegalArgumentException("Host with name '$name' not found")
    }

    override fun getAllHosts(): List<AnsibleHost> {
        return ansibleHostRepository.findAll()
    }

    override fun getEnabledHosts(): List<AnsibleHost> {
        return ansibleHostRepository.findByEnabledTrue()
    }

    override fun getHostsByGroup(group: AnsibleInventoryGroup): List<AnsibleHost> {
        return ansibleHostRepository.findByAnsibleInventoryGroup(group)
    }

    override fun getEnabledHostsByGroup(group: AnsibleInventoryGroup): List<AnsibleHost> {
        return ansibleHostRepository.findByEnabledTrueAndAnsibleInventoryGroup(group)
    }

    override fun getUngroupedHosts(): List<AnsibleHost> {
        return ansibleHostRepository.findByAnsibleInventoryGroupIsNull()
    }

    override fun deleteHost(id: UUID) {
        if (!ansibleHostRepository.existsById(id)) {
            throw NoSuchElementException("Host with ID $id not found")
        }
        ansibleHostRepository.deleteById(id)
    }

    // ========== Inventory Group Management ==========

    override fun createGroup(request: CreateAnsibleInventoryGroupRequest): AnsibleInventoryGroup {
        // Validate the request
        val validationErrors = validateCreateGroupRequest(request)
        if (validationErrors.isNotEmpty()) {
            throw IllegalArgumentException("Group validation failed: ${validationErrors.joinToString(", ")}")
        }

        // Check for duplicate names
        if (ansibleInventoryGroupRepository.existsByName(request.name)) {
            throw IllegalArgumentException("Inventory group with name '${request.name}' already exists")
        }

        // Convert request to entity
        val group = AnsibleInventoryGroup(
            name = request.name.trim(),
            description = request.description?.trim(),
            enabled = request.enabled,
            variables = request.variables?.let { objectMapper.writeValueAsString(it) }
        )

        return ansibleInventoryGroupRepository.save(group)
    }

    override fun updateGroup(id: UUID, request: UpdateAnsibleInventoryGroupRequest): AnsibleInventoryGroup {
        val existingGroup = ansibleInventoryGroupRepository.findById(id).orElse(null)
            ?: throw NoSuchElementException("Inventory group with ID $id not found")

        // Validate the request
        val validationErrors = validateUpdateGroupRequest(request)
        if (validationErrors.isNotEmpty()) {
            throw IllegalArgumentException("Group validation failed: ${validationErrors.joinToString(", ")}")
        }

        // Check for duplicate names (excluding current group)
        if (request.name != existingGroup.name && ansibleInventoryGroupRepository.existsByName(request.name)) {
            throw IllegalArgumentException("Inventory group with name '${request.name}' already exists")
        }

        // Convert request to entity, preserving ID and creation time
        val groupToUpdate = existingGroup.copy(
            name = request.name.trim(),
            description = request.description?.trim(),
            enabled = request.enabled,
            variables = request.variables?.let { objectMapper.writeValueAsString(it) }
        )

        return ansibleInventoryGroupRepository.save(groupToUpdate)
    }

    override fun getGroup(id: UUID): AnsibleInventoryGroup {
        return ansibleInventoryGroupRepository.findById(id).getOrNull() ?: throw NoSuchElementException("Inventory group with ID $id not found")
    }

    override fun getGroupByName(name: String): AnsibleInventoryGroup {
        return ansibleInventoryGroupRepository.findByName(name) ?: throw NoSuchElementException("Inventory group with name '$name' not found")
    }

    override fun getAllGroups(): List<AnsibleInventoryGroup> {
        return ansibleInventoryGroupRepository.findAll()
    }

    override fun getEnabledGroups(): List<AnsibleInventoryGroup> {
        return ansibleInventoryGroupRepository.findByEnabledTrue()
    }

    override fun deleteGroup(id: UUID) {
        val group = ansibleInventoryGroupRepository.findById(id).orElse(null)
            ?: throw NoSuchElementException("Inventory group with ID $id not found")

        // Check if any hosts are assigned to this group
        val hostsInGroup = ansibleHostRepository.findByAnsibleInventoryGroup(group)
        if (hostsInGroup.isNotEmpty()) {
            throw IllegalStateException("Cannot delete inventory group '${group.name}' because it has ${hostsInGroup.size} host(s) assigned to it")
        }

        ansibleInventoryGroupRepository.deleteById(id)
    }

    // ========== Inventory Generation ==========

    override fun generateInventory(): String {
        val hosts = getEnabledHosts()
        val groups = getEnabledGroups()
        return ansibleInventoryGenerator.generateInventory(hosts, groups)
    }

    override fun generateGroupInventory(groupId: UUID): String {
        val group = getGroup(groupId)

        if (!group.enabled) {
            throw IllegalStateException("Cannot generate inventory for disabled group '${group.name}'")
        }

        val hosts = getEnabledHostsByGroup(group)
        return ansibleInventoryGenerator.generateGroupInventory(group, hosts)
    }

    override fun validateInventory(): List<String> {
        val inventory = generateInventory()
        return ansibleInventoryGenerator.validateInventoryContent(inventory)
    }

    // ========== Statistics and Information ==========

    override fun getStatistics(): AnsibleStatistics {
        val totalHosts = ansibleHostRepository.count()
        val enabledHosts = ansibleHostRepository.findByEnabledTrue().size
        val ungroupedHosts = ansibleHostRepository.findByAnsibleInventoryGroupIsNull().size
        val totalGroups = ansibleInventoryGroupRepository.count()
        val enabledGroups = ansibleInventoryGroupRepository.findByEnabledTrue().size

        return AnsibleStatistics(
            totalHosts = totalHosts,
            enabledHosts = enabledHosts,
            disabledHosts = totalHosts - enabledHosts,
            ungroupedHosts = ungroupedHosts,
            totalGroups = totalGroups,
            enabledGroups = enabledGroups,
            disabledGroups = totalGroups - enabledGroups
        )
    }

    /**
     * Build annotation map with custom variables prefixed with ansible_
     */
    private fun buildCustomVariablesAnnotation(customVariables: Map<String, String>?): Map<String, Any> {
        val annotation = mutableMapOf<String, Any>()

        customVariables?.forEach { (key, value) ->
            // Prefix custom variables with ansible_ if not already prefixed
            val prefixedKey = if (key.startsWith("ansible_")) key else "ansible_$key"
            annotation[prefixedKey] = value
        }

        return annotation
    }

    /**
     * Validate CreateAnsibleHostRequest
     */
    private fun validateCreateHostRequest(request: CreateAnsibleHostRequest): List<String> {
        val errors = mutableListOf<String>()

        if (request.name.isBlank()) {
            errors.add("Host name cannot be blank")
        }

        if (request.hostname.isBlank()) {
            errors.add("Hostname cannot be blank")
        }

        if (request.ipAddress.isBlank()) {
            errors.add("IP address cannot be blank")
        }

        // Basic IP address format validation
        if (!isValidIpAddress(request.ipAddress)) {
            errors.add("Invalid IP address format: ${request.ipAddress}")
        }

        if (request.sshPort !in 1..65535) {
            errors.add("SSH port must be between 1 and 65535")
        }

        if (request.sshUsername.isBlank()) {
            errors.add("SSH username cannot be blank")
        }

        if (request.sudoRequired && request.sudoPassword.isNullOrBlank()) {
            errors.add("Sudo password must be provided if sudo is required")
        }

        return errors
    }

    /**
     * Validate UpdateAnsibleHostRequest
     */
    private fun validateUpdateHostRequest(request: UpdateAnsibleHostRequest): List<String> {
        val errors = mutableListOf<String>()

        if (request.name.isBlank()) {
            errors.add("Host name cannot be blank")
        }

        if (request.hostname.isBlank()) {
            errors.add("Hostname cannot be blank")
        }

        if (request.ipAddress.isBlank()) {
            errors.add("IP address cannot be blank")
        }

        // Basic IP address format validation
        if (!isValidIpAddress(request.ipAddress)) {
            errors.add("Invalid IP address format: ${request.ipAddress}")
        }

        if (request.sshPort !in 1..65535) {
            errors.add("SSH port must be between 1 and 65535")
        }

        if (request.sshUsername.isBlank()) {
            errors.add("SSH username cannot be blank")
        }

        if (request.sudoRequired && request.sudoPassword.isNullOrBlank()) {
            errors.add("Sudo password must be provided if sudo is required")
        }

        return errors
    }

    /**
     * Validate CreateAnsibleInventoryGroupRequest
     */
    private fun validateCreateGroupRequest(request: CreateAnsibleInventoryGroupRequest): List<String> {
        val errors = mutableListOf<String>()

        if (request.name.isBlank()) {
            errors.add("Group name cannot be blank")
        }

        // Validate that name follows Ansible inventory naming conventions
        if (!isValidInventoryGroupName(request.name)) {
            errors.add("Invalid inventory group name: ${request.name}. Must contain only letters, numbers, underscores, and hyphens")
        }

        return errors
    }

    /**
     * Validate UpdateAnsibleInventoryGroupRequest
     */
    private fun validateUpdateGroupRequest(request: UpdateAnsibleInventoryGroupRequest): List<String> {
        val errors = mutableListOf<String>()

        if (request.name.isBlank()) {
            errors.add("Group name cannot be blank")
        }

        // Validate that name follows Ansible inventory naming conventions
        if (!isValidInventoryGroupName(request.name)) {
            errors.add("Invalid inventory group name: ${request.name}. Must contain only letters, numbers, underscores, and hyphens")
        }

        return errors
    }

    private fun isValidIpAddress(ip: String): Boolean {
        return try {
            IPAddress(ip)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidInventoryGroupName(name: String): Boolean {
        // Ansible inventory group names should only contain alphanumeric characters, underscores, and hyphens
        // and cannot start with a number
        return name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_-]*$"))
    }
}