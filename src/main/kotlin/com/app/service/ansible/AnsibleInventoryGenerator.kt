package com.app.service.ansible

import com.app.model.AnsibleHost
import com.app.model.AnsibleInventoryGroup
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Path

/**
 * Service responsible for generating Ansible inventory files in INI format.
 * This class follows the single responsibility principle by only handling inventory generation logic.
 *
 * [sshPrivateKeyMaterializationDir]: directory used in `ansible_ssh_private_key_file=`;
 * [DefaultAnsiblePlaybookExecutor] writes vault key material to these paths before each run.
 */
@Component
class AnsibleInventoryGenerator(
    private val objectMapper: ObjectMapper,
    @Value("\${ansible.ssh-private-key-materialization-dir:/tmp/keys}") private val sshPrivateKeyMaterializationDir: String,
) {

    /**
     * Generate Ansible inventory content in INI format
     */
    fun generateInventory(
        hosts: List<AnsibleHost>,
        groups: List<AnsibleInventoryGroup>
    ): String {
        val inventory = StringBuilder()

        // Generate ungrouped hosts section
        val ungroupedHosts = hosts.filter { it.ansibleInventoryGroup == null && it.enabled }
        if (ungroupedHosts.isNotEmpty()) {
            inventory.appendLine("[ungrouped]")
            ungroupedHosts.forEach { host ->
                inventory.appendLine(formatHostEntry(host))
            }
            inventory.appendLine()
        }

        // Generate groups sections
        val enabledGroups = groups.filter { it.enabled }
        enabledGroups.forEach { group ->
            val groupHosts = hosts.filter {
                it.ansibleInventoryGroup?.id == group.id && it.enabled
            }

            if (groupHosts.isNotEmpty()) {
                // Group hosts section
                inventory.appendLine("[${group.name}]")
                groupHosts.forEach { host ->
                    inventory.appendLine(formatHostEntry(host))
                }
                inventory.appendLine()

                // Group variables section
                val groupVars = parseGroupVariables(group.variables)
                if (groupVars.isNotEmpty()) {
                    inventory.appendLine("[${group.name}:vars]")
                    groupVars.forEach { (key, value) ->
                        inventory.appendLine("$key=$value")
                    }
                    inventory.appendLine()
                }
            }
        }

        return inventory.toString()
    }

    /**
     * Format a single host entry with its variables
     */
    private fun formatHostEntry(host: AnsibleHost): String {
        val hostLine = StringBuilder()
        hostLine.append(host.hostname)

        // Add host variables
        val hostVars = mutableMapOf<String, Any>()

        // Basic connection parameters
        hostVars["ansible_host"] = host.ipAddress
        if (host.sshPort != 22) {
            hostVars["ansible_port"] = host.sshPort
        }
        hostVars["ansible_user"] = host.sshUsername

        // SSH key configuration — path must match what [DefaultAnsiblePlaybookExecutor] materializes
        host.sshPrivateKey.let { key ->
            if (key.enabled) {
                val pemPath = Path.of(sshPrivateKeyMaterializationDir.trimEnd('/', '\\'), "${key.id}.pem")
                hostVars["ansible_ssh_private_key_file"] = pemPath.toString()
            }
        }

        // Sudo configuration
        if (host.sudoRequired) {
            hostVars["ansible_become"] = "true"
            hostVars["ansible_become_method"] = "sudo"
            host.sudoPassword?.let {
                hostVars["ansible_become_password"] = it
            }
        }

        // Python interpreter
        host.pythonInterpreter?.let {
            if (it.isNotBlank()) {
                hostVars["ansible_python_interpreter"] = it
            }
        }

        // Add custom variables from annotation
        host.annotation.forEach { (key, value) ->
            if (key.startsWith("ansible_")) {
                hostVars[key] = value
            }
        }

        // Append variables to host line
        if (hostVars.isNotEmpty()) {
            val varsString = hostVars.map { (key, value) ->
                "$key=${formatVariableValue(value)}"
            }.joinToString(" ")
            hostLine.append(" $varsString")
        }

        return hostLine.toString()
    }

    /**
     * Parse group variables from JSON string
     */
    private fun parseGroupVariables(variablesJson: String?): Map<String, Any> {
        if (variablesJson.isNullOrBlank()) {
            return emptyMap()
        }

        return try {
            objectMapper.readValue(
                variablesJson,
                object : TypeReference<Map<String, Any>>() {}
            )
        } catch (e: Exception) {
            // Log error in real implementation
            emptyMap()
        }
    }

    /**
     * Format variable value for inventory file
     */
    private fun formatVariableValue(value: Any): String {
        return when (value) {
            is String -> if (value.contains(" ")) "\"$value\"" else value
            is Boolean -> value.toString().lowercase()
            else -> value.toString()
        }
    }

    /**
     * Generate inventory for a specific group
     */
    fun generateGroupInventory(
        group: AnsibleInventoryGroup,
        hosts: List<AnsibleHost>
    ): String {
        return generateInventory(hosts, listOf(group))
    }

    /**
     * Single-host inventory for one playbook run (e.g. [ping.yml]), using the same host line
     * formatting as bulk inventory (SSH key path, become, python, custom vars).
     */
    fun inventoryForSinglePlaybookTarget(host: AnsibleHost, inventoryGroupName: String): String {
        return buildString {
            appendLine("[$inventoryGroupName]")
            appendLine(formatHostEntry(host))
        }
    }

    /**
     * Validate that generated inventory content is valid
     */
    fun validateInventoryContent(content: String): List<String> {
        val errors = mutableListOf<String>()

        if (content.isBlank()) {
            errors.add("Inventory content cannot be empty")
            return errors
        }

        val lines = content.lines()
        var currentSection: String? = null

        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                return@forEach
            }

            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                // Group header
                val sectionName = trimmed.substring(1, trimmed.length - 1)
                if (sectionName.isBlank()) {
                    errors.add("Empty group name found: $trimmed")
                }
                currentSection = sectionName
            } else {
                // Host or variable entry
                if (currentSection == null) {
                    errors.add("Host entry outside of any group: $trimmed")
                }

                // Basic format validation
                if (!trimmed.contains("=") && currentSection?.endsWith(":vars") == true) {
                    errors.add("Invalid variable format in $currentSection: $trimmed")
                }
            }
        }

        return errors
    }
}