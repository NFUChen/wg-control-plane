package com.app.view.ansible

import com.app.model.AnsibleExecutionJob
import com.app.model.AnsibleExecutionStatus
import java.time.LocalDateTime
import java.util.*

/**
 * API DTOs for Ansible playbook execution jobs (read-only).
 */
data class AnsibleExecutionJobSummaryResponse(
    val id: UUID,
    val playbook: String,
    val status: AnsibleExecutionStatus,
    val startedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val durationSeconds: Long?,
    val exitCode: Int?,
    val successful: Boolean,
    val checkMode: Boolean,
    val verbosity: Int,
    val triggeredBy: String?,
    val notes: String?,
    val createdAt: LocalDateTime,
)

data class AnsibleExecutionJobDetailResponse(
    val id: UUID,
    val playbook: String,
    val status: AnsibleExecutionStatus,
    val startedAt: LocalDateTime?,
    val completedAt: LocalDateTime?,
    val durationSeconds: Long?,
    val exitCode: Int?,
    val successful: Boolean,
    val checkMode: Boolean,
    val verbosity: Int,
    val inventoryContent: String,
    val extraVars: Map<String, Any>,
    val stdout: String?,
    val stderr: String?,
    val executionErrors: List<String>,
    val triggeredBy: String?,
    val notes: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

fun AnsibleExecutionJob.toSummaryResponse(): AnsibleExecutionJobSummaryResponse =
    AnsibleExecutionJobSummaryResponse(
        id = id,
        playbook = playbook,
        status = status,
        startedAt = startedAt,
        completedAt = completedAt,
        durationSeconds = durationSeconds,
        exitCode = exitCode,
        successful = successful,
        checkMode = checkMode,
        verbosity = verbosity,
        triggeredBy = triggeredBy,
        notes = notes,
        createdAt = createdAt,
    )

fun AnsibleExecutionJob.toDetailResponse(): AnsibleExecutionJobDetailResponse =
    AnsibleExecutionJobDetailResponse(
        id = id,
        playbook = playbook,
        status = status,
        startedAt = startedAt,
        completedAt = completedAt,
        durationSeconds = durationSeconds,
        exitCode = exitCode,
        successful = successful,
        checkMode = checkMode,
        verbosity = verbosity,
        inventoryContent = inventoryContent,
        extraVars = extraVars,
        stdout = stdout,
        stderr = stderr,
        executionErrors = executionErrors,
        triggeredBy = triggeredBy,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
