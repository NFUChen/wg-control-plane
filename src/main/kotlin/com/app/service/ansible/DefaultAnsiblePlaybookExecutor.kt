package com.app.service.ansible

import com.app.model.*
import com.app.repository.AnsibleExecutionJobRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists

/**
 * Default implementation of AnsiblePlaybookExecutor.
 * Simple executor focused on core functionality for WireGuard management.
 */
@Service
@Transactional
class DefaultAnsiblePlaybookExecutor(
    private val executionJobRepository: AnsibleExecutionJobRepository,
    private val objectMapper: ObjectMapper,
    private val resourceLoader: ResourceLoader,
    @Value("\${ansible.working-directory:#{null}}") private val ansibleWorkingDirectory: String?,
    @Value("\${ansible.timeout-seconds:3600}") private val defaultTimeoutSeconds: Long = 3600,
    @Value("\${ansible.executable:ansible-playbook}") private val ansibleExecutable: String = "ansible-playbook"
) : AnsiblePlaybookExecutor {

    private val logger = LoggerFactory.getLogger(DefaultAnsiblePlaybookExecutor::class.java)
    private val runningProcesses = ConcurrentHashMap<UUID, Process>()

    companion object {
        private const val DEFAULT_ANSIBLE_DIR = "ansible"
    }

    // ========== Core Execution Methods ==========

    override fun executePlaybook(
        inventoryContent: String,
        playbook: String,
        extraVars: Map<String, Any>,
        checkMode: Boolean,
        verbosity: Int,
        triggeredBy: String?,
        notes: String?
    ): AnsibleExecutionJob {
        logger.info("Executing playbook: $playbook")

        val job = createExecutionJob(inventoryContent, playbook, extraVars, checkMode, verbosity, triggeredBy, notes)
        try {
            executeJobSync(job)
        } catch (e: Exception) {
            logger.error("Error executing playbook: $playbook", e)
            job.markAsCompleted(AnsibleExecutionStatus.FAILED)
            job.executionErrors = listOf(e.message ?: "Unknown error")
            executionJobRepository.save(job)
        }
        return job
    }

    @Async
    override fun executePlaybookAsync(
        inventoryContent: String,
        playbook: String,
        extraVars: Map<String, Any>,
        checkMode: Boolean,
        verbosity: Int,
        triggeredBy: String?,
        notes: String?
    ) {
        logger.info("Starting async execution of playbook: $playbook")

        val job = createExecutionJob(inventoryContent, playbook, extraVars, checkMode, verbosity, triggeredBy, notes)
        executeJobSync(job)
    }

    // ========== Job Management ==========

    override fun cancelExecution(jobId: UUID): Boolean {
        logger.info("Cancelling execution: $jobId")

        val job = executionJobRepository.findById(jobId).orElse(null) ?: return false

        if (!job.isRunning()) {
            return false
        }

        runningProcesses[jobId]?.destroyForcibly()
        runningProcesses.remove(jobId)

        job.markAsCompleted(AnsibleExecutionStatus.CANCELLED)
        executionJobRepository.save(job)

        return true
    }

    override fun getExecutionStatus(jobId: UUID): AnsibleExecutionJob? {
        return executionJobRepository.findById(jobId).orElse(null)
    }

    override fun waitForCompletion(jobId: UUID, timeoutSeconds: Long): AnsibleExecutionJob? {
        val startTime = System.currentTimeMillis()
        val timeoutMillis = timeoutSeconds * 1000

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            val job = getExecutionStatus(jobId)
            if (job != null && job.isCompleted()) {
                return job
            }
            Thread.sleep(1000)
        }
        return null
    }

    override fun getExecutionJob(jobId: UUID): AnsibleExecutionJob? {
        return executionJobRepository.findById(jobId).orElse(null)
    }

    override fun getRunningJobs(): List<AnsibleExecutionJob> {
        return executionJobRepository.findByStatus(AnsibleExecutionStatus.RUNNING)
    }

    override fun retryExecution(originalJobId: UUID, triggeredBy: String?): AnsibleExecutionJob {
        val originalJob = executionJobRepository.findById(originalJobId).orElseThrow {
            IllegalArgumentException("Original job not found: $originalJobId")
        }

        return executePlaybook(
            inventoryContent = originalJob.inventoryContent,
            playbook = originalJob.playbook,
            extraVars = originalJob.extraVars,
            checkMode = originalJob.checkMode,
            verbosity = originalJob.verbosity,
            triggeredBy = triggeredBy,
            notes = "Retry of job ${originalJob.id}"
        )
    }

    // ========== Simple History ==========

    override fun getExecutionHistory(
        limit: Int,
        offset: Int,
        playbook: String?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): List<AnsibleExecutionJob> {
        val pageable = org.springframework.data.domain.PageRequest.of(offset / limit, limit)

        return if (playbook != null) {
            executionJobRepository.findByPlaybook(playbook, pageable).content
        } else {
            executionJobRepository.findAll(pageable).content
        }
    }

    override fun cleanupOldJobs(olderThanDays: Int): Int {
        val cutoffDate = LocalDateTime.now().minusDays(olderThanDays.toLong())
        return executionJobRepository.deleteOldCompletedJobs(cutoffDate)
    }

    // ========== Playbook Utilities ==========

    override fun playbookExists(playbook: String): Boolean {
        return try {
            getPlaybookPath(playbook).exists()
        } catch (e: Exception) {
            false
        }
    }

    override fun listAvailablePlaybooks(): List<String> {
        return try {
            getAnsibleDirectory().toFile().listFiles { file ->
                file.isFile && (file.extension == "yml" || file.extension == "yaml")
            }?.map { it.name }?.sorted() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun validatePlaybook(playbook: String): PlaybookValidationResult {
        val playbookPath = getPlaybookPath(playbook)

        if (!playbookPath.exists()) {
            return PlaybookValidationResult(
                isValid = false,
                errors = listOf("Playbook not found: $playbook"),
                warnings = emptyList(),
                playbookPath = playbookPath.toString()
            )
        }

        return PlaybookValidationResult(
            isValid = true,
            errors = emptyList(),
            warnings = emptyList(),
            playbookPath = playbookPath.toString()
        )
    }

    // ========== Simple Statistics ==========

    override fun getExecutionStatistics(startDate: LocalDateTime, endDate: LocalDateTime): ExecutionStatistics {
        val allJobs = executionJobRepository.findByCreatedAtBetween(startDate, endDate)

        return ExecutionStatistics(
            totalExecutions = allJobs.size.toLong(),
            successfulExecutions = allJobs.count { it.successful }.toLong(),
            failedExecutions = allJobs.count { !it.successful && it.isCompleted() }.toLong(),
            cancelledExecutions = allJobs.count { it.status == AnsibleExecutionStatus.CANCELLED }.toLong(),
            averageDurationSeconds = allJobs.mapNotNull { it.durationSeconds }.average().takeIf { !it.isNaN() },
            totalHostsProcessed = allJobs.size.toLong(), // Simplified
            successfulHosts = allJobs.count { it.successful }.toLong(),
            failedHosts = allJobs.count { !it.successful }.toLong(),
            unreachableHosts = 0, // Simplified
            mostUsedPlaybooks = emptyList(),
            periodStart = startDate,
            periodEnd = endDate
        )
    }

    // ========== Private Methods ==========

    private fun createExecutionJob(
        inventoryContent: String,
        playbook: String,
        extraVars: Map<String, Any>,
        checkMode: Boolean,
        verbosity: Int,
        triggeredBy: String?,
        notes: String?
    ): AnsibleExecutionJob {
        return AnsibleExecutionJob(
            playbook = playbook,
            inventoryContent = inventoryContent,
            extraVars = extraVars,
            checkMode = checkMode,
            verbosity = verbosity,
            triggeredBy = triggeredBy,
            notes = notes
        )
    }

    private fun executeJobSync(job: AnsibleExecutionJob) {
        if (!playbookExists(job.playbook)) {
            throw IllegalArgumentException("Playbook not found: ${job.playbook}")
        }

        job.markAsStarted()
        executionJobRepository.save(job)

        val tempInventoryFile = createTempInventoryFile(job.inventoryContent)

        try {
            val command = buildCommand(job, tempInventoryFile.absolutePath)
            val result = executeCommand(command, job.id)

            val status = if (result.exitCode == 0) AnsibleExecutionStatus.SUCCESS else AnsibleExecutionStatus.FAILED

            job.markAsCompleted(status)
            job.updateResults(
                exitCode = result.exitCode,
                stdout = result.output,
                stderr = result.errorOutput,
                errors = if (result.exitCode != 0) listOf(result.errorOutput) else emptyList()
            )

        } finally {
            tempInventoryFile.delete()
            runningProcesses.remove(job.id)
            executionJobRepository.save(job)
        }
    }

    private fun buildCommand(job: AnsibleExecutionJob, inventoryFile: String): List<String> {
        val command = mutableListOf<String>()
        command.add(ansibleExecutable)
        command.add("-i")
        command.add(inventoryFile)

        if (job.verbosity > 0) {
            command.add("-" + "v".repeat(job.verbosity))
        }

        if (job.extraVars.isNotEmpty()) {
            command.add("--extra-vars")
            command.add(objectMapper.writeValueAsString(job.extraVars))
        }

        if (job.checkMode) {
            command.add("--check")
        }

        command.add(getPlaybookPath(job.playbook).toString())
        return command
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String,
        val errorOutput: String
    )

    private fun executeCommand(command: List<String>, jobId: UUID): CommandResult {
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(getAnsibleDirectory().toFile())

        val env = processBuilder.environment()
        env["ANSIBLE_HOST_KEY_CHECKING"] = "False"

        val process = processBuilder.start()
        runningProcesses[jobId] = process

        val finished = process.waitFor(defaultTimeoutSeconds, TimeUnit.SECONDS)

        if (!finished) {
            process.destroyForcibly()
            throw RuntimeException("Command timed out")
        }

        val output = process.inputStream.bufferedReader().readText()
        val errorOutput = process.errorStream.bufferedReader().readText()

        return CommandResult(process.exitValue(), output, errorOutput)
    }


    private fun createTempInventoryFile(inventoryContent: String): File {
        val tempFile = Files.createTempFile("ansible-inventory-", ".ini").toFile()
        tempFile.writeText(inventoryContent)
        return tempFile
    }

    private fun getAnsibleDirectory(): Path {
        return if (ansibleWorkingDirectory != null) {
            Paths.get(ansibleWorkingDirectory)
        } else {
            val resource = resourceLoader.getResource("classpath:$DEFAULT_ANSIBLE_DIR")
            Paths.get(resource.uri)
        }
    }

    private fun getPlaybookPath(playbook: String): Path {
        return getAnsibleDirectory().resolve(playbook)
    }
}