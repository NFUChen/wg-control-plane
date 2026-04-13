package com.app.service.terraform

import com.app.model.RouteTableModule
import com.app.model.TerraformExecutionJob
import com.app.model.TerraformOperation
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime
import java.util.*

/**
 * Service interface for executing Terraform operations.
 * Focused purely on execution capabilities - no business logic for specific modules.
 */
interface TerraformExecutor {

    // ========== Core Execution Methods ==========

    /**
     * Execute a Terraform operation synchronously
     * Blocks until completion or timeout
     */
    fun executeOperation(
        module: RouteTableModule,
        operation: TerraformOperation,
        providerIdentifier: String,
        vars: Map<String, Any> = emptyMap(),
        autoApprove: Boolean = false,
        triggeredBy: String? = null,
        notes: String? = null
    ): TerraformExecutionJob

    /**
     * Execute a Terraform operation asynchronously
     * Returns immediately with a job that can be monitored
     */
    fun executeOperationAsync(
        module: RouteTableModule,
        operation: TerraformOperation,
        providerIdentifier: String,
        vars: Map<String, Any> = emptyMap(),
        autoApprove: Boolean = false,
        triggeredBy: String? = null,
        notes: String? = null
    )

    // ========== Job Management ==========

    /**
     * Cancel a running execution job
     */
    fun cancelExecution(jobId: UUID): Boolean

    /**
     * Get current status of an execution job
     */
    fun getExecutionStatus(jobId: UUID): TerraformExecutionJob?

    /**
     * Get execution job by ID
     */
    fun getExecutionJob(jobId: UUID): TerraformExecutionJob?

    /**
     * Get currently running jobs
     */
    fun getRunningJobs(): List<TerraformExecutionJob>

    /**
     * Retry a failed execution job with same parameters
     */
    fun retryExecution(originalJobId: UUID, triggeredBy: String? = null): TerraformExecutionJob

    // ========== Execution History ==========

    /**
     * Get execution history with filtering
     */
    fun getExecutionHistory(
        pageable: Pageable,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null
    ): Page<TerraformExecutionJob>
}