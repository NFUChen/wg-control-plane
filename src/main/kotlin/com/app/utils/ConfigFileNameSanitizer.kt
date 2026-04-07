package com.app.utils

object ConfigFileNameSanitizer {
    private val illegalChars = setOf('.', ',', '/', '?', '<', '>', '\\', ':', '*', '|', '"', '\n', '\r', '\t')
    private val reservedNames = setOf(
        "con", "nul", "prn", "aux", "com1", "com2", "com3", "com4",
        "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2",
        "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
    )

    fun sanitize(
        originalName: String,
        reservedNamePrefix: String,
        fallback: String
    ): String {
        var sanitized = originalName
            .replace(Regex("\\s+"), "_")
            .filter { it !in illegalChars }
            .take(50)

        if (sanitized.lowercase() in reservedNames) {
            sanitized = "${reservedNamePrefix}_$sanitized"
        }

        return sanitized.ifBlank { fallback }
    }
}
