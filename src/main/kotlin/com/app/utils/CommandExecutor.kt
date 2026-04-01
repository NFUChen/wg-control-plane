package com.app.utils

import org.springframework.stereotype.Component

/**
 * Command execution result
 */
data class CommandResult(
    val exitCode: Int,
    val output: String
)

/**
 * Interface for executing system commands
 * Allows for easy mocking in tests
 */
interface CommandExecutor {
    /**
     * Execute a command with optional stdin input
     *
     * @param command The command and arguments to execute
     * @param input Optional input to write to stdin
     * @return CommandResult containing exit code and output
     */
    fun execute(vararg command: String, input: String? = null): CommandResult
}

/**
 * Default implementation using ProcessBuilder
 */
@Component
class DefaultCommandExecutor : CommandExecutor {

    override fun execute(vararg command: String, input: String?): CommandResult {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()

        // If input data is needed, write to stdin
        input?.let { inputData ->
            process.outputStream.use { outputStream ->
                outputStream.write(inputData.toByteArray())
                outputStream.flush()
            }
        }

        // Wait for process completion and read result
        val exitCode = process.waitFor()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }

        return CommandResult(exitCode, output)
    }
}
