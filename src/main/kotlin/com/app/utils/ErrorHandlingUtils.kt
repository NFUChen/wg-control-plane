package com.app.utils

import org.slf4j.Logger

/**
 * Utility object for error handling and safe method calls
 */
object ErrorHandlingUtils {

    /**
     * Safe call with custom error message
     */
    inline fun <T> safeCall(logger: Logger, errorMessage: String, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            logger.error("$errorMessage: ${e.message}", e)
            throw RuntimeException(errorMessage, e)
        }
    }

    /**
     * Safe call that throws exception on failure
     */
    inline fun <T> safeCall(logger: Logger, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            logger.error("Operation failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * Safe call that only logs errors (for rollback operations)
     */
    inline fun safeCallSilent(logger: Logger, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logger.error("Operation failed: ${e.message}", e)
        }
    }
}