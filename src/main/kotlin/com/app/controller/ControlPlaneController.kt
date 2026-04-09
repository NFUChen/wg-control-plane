package com.app.controller

import com.app.security.config.ControlPlaneMode
import com.app.service.validation.WireGuardValidationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for control plane mode information
 */
@RestController
@RequestMapping("/api/public/control-plane")
class ControlPlaneController(
    private val validationService: WireGuardValidationService
) {

    /**
     * Get current control plane mode and configuration
     */
    @GetMapping("/mode")
    fun getControlPlaneMode(): ResponseEntity<ControlPlaneModeResponse> {
        val response = ControlPlaneModeResponse(
            mode = validationService.getCurrentMode().name,
            allowsLocalOperations = validationService.areLocalOperationsAllowed(),
            description = when (validationService.getCurrentMode()) {
                ControlPlaneMode.HYBRID -> "Supports both local and remote operations"
                ControlPlaneMode.PURE_REMOTE -> "Only remote operations allowed (Ansible/Agent mode)"
            }
        )

        return ResponseEntity.ok(response)
    }
}

/**
 * Response DTO for control plane mode information
 */
data class ControlPlaneModeResponse(
    val mode: String,
    val allowsLocalOperations: Boolean,
    val description: String
)