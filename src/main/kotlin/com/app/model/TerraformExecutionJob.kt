package com.app.model

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

enum class RouteTableModule(val path: String) {
    Azure("/opt/terraform/modules/azure"),
    Aws("/opt/terraform/modules/aws"),
}

enum class TerraformOperation {
    APPLY,
    DESTROY
}

enum class TerraformExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}

@Entity
@Table(name = "terraform_execution_jobs")
data class TerraformExecutionJob(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, columnDefinition = "TEXT")
    val module: RouteTableModule,

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false)
    val operation: TerraformOperation,

    @Column(name = "provider_identifier", nullable = false)
    val providerIdentifier: String,

    @Column(name = "auto_approve", nullable = false)
    val autoApprove: Boolean = false,

    @Column(name = "vars", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    val vars: Map<String, Any> = emptyMap(),

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: TerraformExecutionStatus = TerraformExecutionStatus.PENDING,

    @Column(name = "started_at")
    var startedAt: LocalDateTime? = null,

    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null,

    @Column(name = "duration_seconds")
    var durationSeconds: Long? = null,

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
    fun isRunning(): Boolean = status == TerraformExecutionStatus.RUNNING

    /**
     * Check if the job has completed (success or failure)
     */
    fun isCompleted(): Boolean = status in listOf(
        TerraformExecutionStatus.SUCCESS,
        TerraformExecutionStatus.FAILED,
        TerraformExecutionStatus.CANCELLED
    )

    /**
     * Check if the job was successful
     */
    fun isSuccessful(): Boolean = status == TerraformExecutionStatus.SUCCESS

    /**
     * Mark job as started
     */
    fun markAsStarted() {
        status = TerraformExecutionStatus.RUNNING
        startedAt = LocalDateTime.now()
    }

    /**
     * Mark job as completed with status
     */
    fun markAsCompleted(finalStatus: TerraformExecutionStatus) {
        status = finalStatus
        completedAt = LocalDateTime.now()
        successful = finalStatus == TerraformExecutionStatus.SUCCESS

        startedAt?.let { start ->
            durationSeconds = java.time.Duration.between(start, completedAt).seconds
        }
    }

    /**
     * Update execution results
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