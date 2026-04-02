package com.app.service.ansible

import com.app.model.AnsibleExecutionJob
import java.time.LocalDateTime
import java.util.*

/**
 * Service interface for executing Ansible playbooks.
 * Focused purely on execution capabilities - no business logic for specific playbooks.
 */
interface AnsiblePlaybookExecutor {

    // ========== Core Execution Methods ==========

    /**
     * Execute a playbook synchronously
     * Blocks until completion or timeout
     */
    fun executePlaybook(
        inventoryContent: String,
        playbook: String,
        extraVars: Map<String, Any> = emptyMap(),
        checkMode: Boolean = false,
        verbosity: Int = 0,
        triggeredBy: String? = null,
        notes: String? = null
    ): AnsibleExecutionJob

    /**
     * Execute a playbook asynchronously
     * Returns immediately with a job that can be monitored
     */
    fun executePlaybookAsync(
        inventoryContent: String,
        playbook: String,
        extraVars: Map<String, Any> = emptyMap(),
        checkMode: Boolean = false,
        verbosity: Int = 0,
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
    fun getExecutionStatus(jobId: UUID): AnsibleExecutionJob?

    /**
     * Wait for execution to complete (with timeout)
     */
    fun waitForCompletion(jobId: UUID, timeoutSeconds: Long = 3600): AnsibleExecutionJob?

    /**
     * Get execution job by ID
     */
    fun getExecutionJob(jobId: UUID): AnsibleExecutionJob?

    /**
     * Get currently running jobs
     */
    fun getRunningJobs(): List<AnsibleExecutionJob>

    /**
     * Retry a failed execution job with same parameters
     */
    fun retryExecution(originalJobId: UUID, triggeredBy: String? = null): AnsibleExecutionJob

    // ========== Execution History ==========

    /**
     * Get execution history with filtering
     */
    fun getExecutionHistory(
        limit: Int = 50,
        offset: Int = 0,
        playbook: String? = null,
        startDate: LocalDateTime? = null,
        endDate: LocalDateTime? = null
    ): List<AnsibleExecutionJob>

    /**
     * Clean up old completed jobs
     */
    fun cleanupOldJobs(olderThanDays: Int = 30): Int

    // ========== Playbook Utilities ==========

    /**
     * Check if a playbook file exists
     */
    fun playbookExists(playbook: String): Boolean

    /**
     * List available playbook files
     */
    fun listAvailablePlaybooks(): List<String>

    /**
     * Validate playbook syntax
     */
    fun validatePlaybook(playbook: String): PlaybookValidationResult

    // ========== Statistics ==========

    /**
     * Get execution statistics for time period
     */
    fun getExecutionStatistics(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): ExecutionStatistics
}

/**
 * Validation result for playbook syntax
 */
data class PlaybookValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>,
    val playbookPath: String
)

/**
 * Overall execution statistics for a time period
 */
data class ExecutionStatistics(
    val totalExecutions: Long,
    val successfulExecutions: Long,
    val failedExecutions: Long,
    val cancelledExecutions: Long,
    val averageDurationSeconds: Double?,
    val totalHostsProcessed: Long,
    val successfulHosts: Long,
    val failedHosts: Long,
    val unreachableHosts: Long,
    val mostUsedPlaybooks: List<String>,
    val periodStart: LocalDateTime,
    val periodEnd: LocalDateTime
)