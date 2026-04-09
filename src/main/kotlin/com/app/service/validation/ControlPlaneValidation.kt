package com.app.service.validation

import com.app.view.AddClientRequest
import com.app.view.CreateServerRequest
import com.app.view.UpdateServerRequest
import java.util.*

/**
 * Validation result for control plane operations
 */
data class ValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val suggestions: List<String> = emptyList(),
    val warningMessage: String? = null
)

/**
 * Strategy interface for validating operations based on control plane mode
 */
interface ControlPlaneValidationStrategy {

    /**
     * Validate server creation request
     */
    fun validateServerCreation(request: CreateServerRequest): ValidationResult

    /**
     * Validate client creation request
     */
    fun validateClientCreation(request: AddClientRequest): ValidationResult

    /**
     * Validate server update request
     */
    fun validateServerUpdate(serverId: UUID, request: UpdateServerRequest): ValidationResult

    /**
     * Enrich server creation request with default values or modifications
     */
    fun enrichServerRequest(request: CreateServerRequest): CreateServerRequest

    /**
     * Enrich client creation request with default values or modifications
     */
    fun enrichClientRequest(request: AddClientRequest): AddClientRequest
}

/**
 * Custom exception for control plane validation failures
 */
class ControlPlaneValidationException(
    message: String,
    val suggestions: List<String> = emptyList(),
    val warningMessage: String? = null
) : RuntimeException(message)