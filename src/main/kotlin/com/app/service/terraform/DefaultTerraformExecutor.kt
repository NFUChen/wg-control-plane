package com.app.service.terraform

import com.app.model.RouteTableModule
import com.app.model.TerraformExecutionJob
import com.app.model.TerraformExecutionStatus
import com.app.model.TerraformOperation
import com.app.repository.TerraformExecutionJobRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Default implementation of TerraformExecutor.
 * Simple executor focused on core Terraform operations.
 */
@Service
@Transactional
class DefaultTerraformExecutor(
    private val executionJobRepository: TerraformExecutionJobRepository,
    @Value("\${terraform.timeout-seconds}") private val defaultTimeoutSeconds: Long = 3600,
    @Value("\${terraform.executable:terraform}") private val terraformExecutable: String = "terraform"
) : TerraformExecutor {

    private val logger = LoggerFactory.getLogger(DefaultTerraformExecutor::class.java)
    private val runningProcesses = ConcurrentHashMap<UUID, Process>()

    // ========== Core Execution Methods ==========

    /**
     * Runs in a separate transaction so job rows commit even when the caller rolls back after a failed execution.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun executeOperation(
        module: RouteTableModule,
        operation: TerraformOperation,
        providerIdentifier: String,
        vars: Map<String, Any>,
        autoApprove: Boolean,
        triggeredBy: String?,
        notes: String?
    ): TerraformExecutionJob {
        logger.info("Executing Terraform operation: {} for module: {}", operation, module)

        val job = createExecutionJob(
            module,
            operation,
            providerIdentifier,
            vars,
            autoApprove,
            triggeredBy,
            notes
        )

        try {
            executeJobSync(job)
        } catch (e: Exception) {
            logger.error("Error executing Terraform operation: $operation", e)
            job.markAsCompleted(TerraformExecutionStatus.FAILED)
            job.executionErrors = listOf(e.message ?: "Unknown error")
            executionJobRepository.save(job)
        }
        return job
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun executeOperationAsync(
        module: RouteTableModule,
        operation: TerraformOperation,
        providerIdentifier: String,
        vars: Map<String, Any>,
        autoApprove: Boolean,
        triggeredBy: String?,
        notes: String?
    ) {
        logger.info("Starting async execution of Terraform operation: {}", operation)

        val job = createExecutionJob(
            module,
            operation,
            providerIdentifier,
            vars,
            autoApprove,
            triggeredBy,
            notes
        )
        executeJobSync(job)
    }

    // ========== Job Management ==========

    override fun cancelExecution(jobId: UUID): Boolean {
        logger.info("Cancelling execution: $jobId")

        val job = executionJobRepository.findById(jobId).orElse(null) ?: return false

        if (!job.isRunning()) {
            logger.warn("Cannot cancel job $jobId because it is not running (current status: ${job.status})")
            return false
        }

        runningProcesses[jobId]?.destroyForcibly()
        runningProcesses.remove(jobId)

        job.markAsCompleted(TerraformExecutionStatus.CANCELLED)
        executionJobRepository.save(job)

        return true
    }

    override fun getExecutionStatus(jobId: UUID): TerraformExecutionJob? {
        return executionJobRepository.findById(jobId).orElse(null)
    }

    override fun getExecutionJob(jobId: UUID): TerraformExecutionJob? {
        return executionJobRepository.findById(jobId).orElse(null)
    }

    override fun getRunningJobs(): List<TerraformExecutionJob> {
        return executionJobRepository.findByStatus(TerraformExecutionStatus.RUNNING)
    }

    override fun retryExecution(originalJobId: UUID, triggeredBy: String?): TerraformExecutionJob {
        val originalJob = executionJobRepository.findById(originalJobId).orElseThrow {
            IllegalArgumentException("Original job not found: $originalJobId")
        }

        return executeOperation(
            module = originalJob.module,
            operation = originalJob.operation,
            providerIdentifier = originalJob.providerIdentifier,
            vars = originalJob.vars,
            autoApprove = originalJob.autoApprove,
            triggeredBy = triggeredBy,
            notes = "Retry of job ${originalJob.id}"
        )
    }

    // ========== Execution History ==========

    override fun getExecutionHistory(
        pageable: Pageable,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): Page<TerraformExecutionJob> {
        return executionJobRepository.findAll(pageable)

    }

    // ========== Private Methods ==========

    private fun createExecutionJob(
        module: RouteTableModule,
        operation: TerraformOperation,
        providerIdentifier: String,
        vars: Map<String, Any>,
        autoApprove: Boolean,
        triggeredBy: String?,
        notes: String?
    ): TerraformExecutionJob {
        return TerraformExecutionJob(
            module = module,
            operation = operation,
            providerIdentifier = providerIdentifier,
            vars = vars,
            autoApprove = autoApprove,
            triggeredBy = triggeredBy,
            notes = notes
        )
    }

    private fun executeJobSync(job: TerraformExecutionJob) {
        job.markAsStarted()
        executionJobRepository.save(job)

        try {
            // Step 1: Initialize Terraform
            val initResult = executeTerraformInit(job)
            if (initResult.exitCode != 0) {
                job.markAsCompleted(TerraformExecutionStatus.FAILED)
                job.updateResults(
                    exitCode = initResult.exitCode,
                    stdout = initResult.output,
                    stderr = initResult.errorOutput,
                    errors = listOf("Terraform init failed: ${initResult.errorOutput}")
                )
                executionJobRepository.save(job)
                return
            }

            // Step 2: Execute the actual operation
            val operationResult = executeTerraformOperation(job)
            val status = if (operationResult.exitCode == 0) {
                TerraformExecutionStatus.SUCCESS
            } else {
                TerraformExecutionStatus.FAILED
            }

            job.markAsCompleted(status)
            job.updateResults(
                exitCode = operationResult.exitCode,
                stdout = operationResult.output,
                stderr = operationResult.errorOutput,
                errors = if (operationResult.exitCode != 0) {
                    listOf(operationResult.errorOutput)
                } else {
                    emptyList()
                }
            )

        } finally {
            runningProcesses.remove(job.id)
            executionJobRepository.save(job)
        }
    }

    private fun executeTerraformInit(job: TerraformExecutionJob): CommandResult {
        val command = listOf(
            terraformExecutable,
            "init",
            "-no-color"
        )

        return executeCommand(command, job.id, job.module.path)
    }

    private fun executeTerraformOperation(job: TerraformExecutionJob): CommandResult {
        val command = buildTerraformCommand(job)
        return executeCommand(command, job.id, job.module.path)
    }

    private fun buildTerraformCommand(job: TerraformExecutionJob): List<String> {
        val command = mutableListOf<String>()
        command.add(terraformExecutable)

        // Add operation
        when (job.operation) {
            TerraformOperation.APPLY -> command.add("apply")
            TerraformOperation.DESTROY -> command.add("destroy")
        }

        // Add no-color flag
        command.add("-no-color")

        // Add auto-approve for apply/destroy if requested
        if (job.autoApprove) {
            command.add("-auto-approve")
        }

        // Add variables
        job.vars.forEach { (key, value) ->
            command.add("-var")
            command.add("$key=$value")
        }

        return command
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String,
        val errorOutput: String
    )

    private fun executeCommand(command: List<String>, jobId: UUID, workingDirectory: String): CommandResult {
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(Paths.get(workingDirectory).toFile())

        val env = processBuilder.environment()
        // Disable interactive prompts
        env["TF_INPUT"] = "false"
        // Disable color in output
        env["TF_CLI_ARGS"] = "-no-color"

        logger.debug("Executing Terraform command: ${command.joinToString(" ")}")

        val process = processBuilder.start()
        runningProcesses[jobId] = process

        val finished = process.waitFor(defaultTimeoutSeconds, TimeUnit.SECONDS)

        if (!finished) {
            process.destroyForcibly()
            throw RuntimeException("Terraform command timed out after $defaultTimeoutSeconds seconds")
        }

        val output = process.inputStream.bufferedReader().readText()
        val errorOutput = process.errorStream.bufferedReader().readText()

        logger.debug("Terraform command exit code: ${process.exitValue()}")

        return CommandResult(process.exitValue(), output, errorOutput)
    }
}