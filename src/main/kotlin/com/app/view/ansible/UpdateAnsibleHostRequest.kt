package com.app.view.ansible

import java.util.*

/**
 * Request DTO for updating an existing Ansible host
 */
data class UpdateAnsibleHostRequest(
    val hostname: String,
    val ipAddress: String,
    val sshPort: Int = 22,
    val sshUsername: String,
    val sshPrivateKeyId: UUID,
    val ansibleInventoryGroupId: UUID? = null,
    val sudoRequired: Boolean = true,
    val sudoPassword: String? = null,
    val pythonInterpreter: String? = "/usr/bin/python3",
    val enabled: Boolean = true,
    val tags: List<String>? = null,
    val description: String? = null,
    val customVariables: Map<String, String>? = null
)