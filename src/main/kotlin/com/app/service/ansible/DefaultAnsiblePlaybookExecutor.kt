package com.app.service.ansible

import com.app.model.AnsibleExecutionJob
import com.app.model.AnsibleExecutionStatus
import com.app.model.PrivateKey
import com.app.repository.AnsibleExecutionJobRepository
import com.app.repository.PrivateKeyRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
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
    private val privateKeyRepository: PrivateKeyRepository,
    private val objectMapper: ObjectMapper,
    @Value("\${ansible.working-directory}") private val ansibleWorkingDirectory: String = "/opt/ansible",
    @Value("\${ansible.timeout-seconds}") private val defaultTimeoutSeconds: Long = 3600,
    @Value("\${ansible.executable:ansible-playbook}") private val ansibleExecutable: String = "ansible-playbook"
) : AnsiblePlaybookExecutor {

    private val logger = LoggerFactory.getLogger(DefaultAnsiblePlaybookExecutor::class.java)
    private val runningProcesses = ConcurrentHashMap<UUID, Process>()

    companion object {

        /** ansible_ssh_private_key_file=... in INI inventory */
        private val ANSIBLE_SSH_KEY_FILE_PATTERN = Regex(
            """ansible_ssh_private_key_file\s*=\s*("([^"]+)"|(\S+))"""
        )
    }

    // ========== Core Execution Methods ==========

    /**
     * Runs in a separate transaction so job rows commit even when the caller
     * (e.g. [com.app.service.AnsibleWireGuardManagementService]) rolls back after a failed deploy.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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

        // Not a full INI validator — we only react to `ansible_ssh_private_key_file=` paths: for each path we either
        // skip (non-.pem or non-UUID basename, with a warning) or require a matching [PrivateKey] in the DB and a
        // successful write. Missing key / I/O failure throws → job fails here before ansible runs.
        val materializedKeyFiles = try {
            materializePrivateKeyFilesFromInventory(job.inventoryContent)
        } catch (e: Exception) {
            logger.error("Failed to materialize SSH private keys for job ${job.id}", e)
            job.markAsCompleted(AnsibleExecutionStatus.FAILED)
            job.executionErrors = listOf(e.message ?: "Failed to write SSH private key files")
            executionJobRepository.save(job)
            return
        }

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
            materializedKeyFiles.forEach { f ->
                try {
                    if (f.exists()) f.delete()
                } catch (e: Exception) {
                    logger.warn("Could not delete materialized key file ${f.absolutePath}: ${e.message}")
                }
            }
            runningProcesses.remove(job.id)
            executionJobRepository.save(job)
        }
    }

    /**
     * Materializes SSH private key **files on disk** so `ansible-playbook` can use paths already declared in the
     * inventory string. Ansible only reads PEM paths from the inventory; it does not load secrets from our DB.
     *
     * **Input — [inventoryContent]:**
     * A fragment of Ansible INI inventory (what we persist on [AnsibleExecutionJob] and pass to `ansible-playbook -i`).
     * We scan for host vars `ansible_ssh_private_key_file=...` (quoted or unquoted). Those paths must match what
     * [AnsibleInventoryGenerator] writes: materialization directory + key id + `.pem` (see `ansible.ssh-private-key-materialization-dir`).
     *
     * **Output — return value:**
     * Every [File] we **created or overwrote** in this call, so the outer `finally` can delete them after the run
     * (keys must not linger on disk). Returns an empty list when the inventory contains no such variables.
     *
     * **Why parse the inventory string (instead of passing key IDs separately):**
     * - **Same snapshot as Ansible:** The job stores the exact inventory text used for the run; retries and logs
     *   replay that string. Deriving paths from it keeps one contract and avoids a parallel parameter (e.g. a
     *   separate collection of key IDs) that could disagree with the persisted inventory.
     * - **Several hosts / several keys:** Distinct paths in one inventory are all materialized.
     * - **Filename convention:** The basename is `privateKeyId.pem`, matching [PrivateKey.id], so we load
     *   [PrivateKey.content] without extra fields on the executor.
     *
     * Paths that are not `*.pem` or whose basename is not a UUID are skipped with a warning.
     */
    private fun materializePrivateKeyFilesFromInventory(inventoryContent: String): List<File> {
        val paths = LinkedHashSet<String>()
        ANSIBLE_SSH_KEY_FILE_PATTERN.findAll(inventoryContent).forEach { m ->
            val raw = m.groupValues[2].ifEmpty { m.groupValues[3] }.trim()
            if (raw.isNotEmpty()) paths.add(raw)
        }
        if (paths.isEmpty()) return emptyList()

        val written = mutableListOf<File>()
        for (pathStr in paths) {
            val fileName = Path.of(pathStr).fileName.toString()
            if (!fileName.endsWith(".pem")) {
                logger.warn("Skipping non-.pem ansible_ssh_private_key_file: $pathStr")
                continue
            }
            val idStr = fileName.removeSuffix(".pem")
            val keyId = try {
                UUID.fromString(idStr)
            } catch (_: IllegalArgumentException) {
                logger.warn("Skipping ansible_ssh_private_key_file without UUID file name: $pathStr")
                continue
            }
            val key = privateKeyRepository.findById(keyId).orElseThrow {
                IllegalStateException("Private key $keyId not found (inventory references $pathStr)")
            }
            val file = File(pathStr)
            file.parentFile?.mkdirs()
            file.writeText(key.content)
            try {
                Files.setPosixFilePermissions(
                    file.toPath(),
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                )
            } catch (_: Exception) {
                // non-POSIX FS
            }
            written.add(file)
            logger.debug("Materialized SSH private key for job to {}", file.absolutePath)
        }
        return written
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
        // Matches [defaults] interpreter_python = auto_silent — suppresses discovery WARNING on stderr
        env["ANSIBLE_INTERPRETER_PYTHON"] = "auto_silent"
        // Subprocess has no TTY; without this Ansible omits ANSI and logs look plain
        env["ANSIBLE_FORCE_COLOR"] = "true"

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
        return Paths.get(ansibleWorkingDirectory)
    }

    private fun getPlaybookPath(playbook: String): Path {
        return getAnsibleDirectory().resolve(playbook)
    }
}