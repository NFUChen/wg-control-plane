package com.app.view.ansible

/**
 * Request DTO for updating an existing Ansible inventory group
 */
data class UpdateAnsibleInventoryGroupRequest(
    val name: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val variables: Map<String, Any>? = null
)