package com.app.view.ansible

/**
 * Request DTO for creating a new Ansible inventory group
 */
data class CreateAnsibleInventoryGroupRequest(
    val name: String,
    val description: String? = null,
    val enabled: Boolean = true,
    val variables: Map<String, Any>? = null
)