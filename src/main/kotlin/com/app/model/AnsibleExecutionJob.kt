package com.app.model

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.*

/**
 * Represents an Ansible playbook execution job for WireGuard management.
 * Simplified to focus on essential execution tracking.
 */
@Entity
@Table(name = "ansible_execution_jobs")
data class AnsibleExecutionJob(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "playbook", nullable = false)
    val playbook: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: AnsibleExecutionStatus = AnsibleExecutionStatus.PENDING,

    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    @Column(name = "duration_seconds")
    var durationSeconds: Long? = null,

    // Execution Parameters
    @Column(name = "inventory_content", columnDefinition = "TEXT")
    val inventoryContent: String,

    @Column(name = "extra_vars", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    val extraVars: Map<String, Any> = emptyMap(),

    @Column(name = "check_mode", nullable = false)
    val checkMode: Boolean = false,

    @Column(name = "verbosity", nullable = false)
    val verbosity: Int = 0,

    // Execution Results
    @Column(name = "exit_code")
    var exitCode: Int? = null,

    @Column(name = "stdout", columnDefinition = "TEXT")
    var stdout: String? = null,

    @Column(name = "stderr", columnDefinition = "TEXT")
    var stderr: String? = null,

    @Column(name = "execution_errors", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    var executionErrors: List<String> = emptyList(),

    // Simple execution tracking
    @Column(name = "successful", nullable = false)
    var successful: Boolean = false,

    // Context information
    @Column(name = "triggered_by")
    val triggeredBy: String? = null,

    @Column(name = "notes")
    val notes: String? = null,

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {

    /**
     * Check if the job is currently running
     */
    fun isRunning(): Boolean = status == AnsibleExecutionStatus.RUNNING

    /**
     * Check if the job has completed (success or failure)
     */
    fun isCompleted(): Boolean = status in listOf(
        AnsibleExecutionStatus.SUCCESS,
        AnsibleExecutionStatus.FAILED,
        AnsibleExecutionStatus.CANCELLED
    )

    /**
     * Check if the job was successful
     */
    fun isSuccessful(): Boolean = status == AnsibleExecutionStatus.SUCCESS

    /**
     * Mark job as started
     */
    fun markAsStarted() {
        status = AnsibleExecutionStatus.RUNNING
        startedAt = LocalDateTime.now()
    }

    /**
     * Mark job as completed with status
     */
    fun markAsCompleted(finalStatus: AnsibleExecutionStatus) {
        status = finalStatus
        completedAt = LocalDateTime.now()
        successful = finalStatus == AnsibleExecutionStatus.SUCCESS

        startedAt?.let { start ->
            durationSeconds = java.time.Duration.between(start, completedAt).seconds
        }
    }

    /**
     * Update execution results - simplified
     */
    fun updateResults(
        exitCode: Int,
        stdout: String?,
        stderr: String?,
        errors: List<String> = emptyList()
    ) {
        this.exitCode = exitCode
        this.stdout = stdout
        this.stderr = stderr
        this.executionErrors = errors
        this.successful = exitCode == 0
    }
}

/**
 * Simplified execution status enumeration
 */
enum class AnsibleExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}