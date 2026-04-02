package com.app.security.service.email

/**
 * Abstract email service; supports multiple providers and avoids vendor lock-in.
 */
interface EmailService {
    /**
     * Send an email.
     * @param email Message content
     * @return Send result
     */
    fun sendEmail(email: Email): EmailSendResult

    /**
     * Send multiple emails.
     * @param emails Messages to send
     * @return Results per message
     */
    fun sendEmails(emails: List<Email>): List<EmailSendResult>

    /**
     * Validate email address format.
     * @param emailAddress Address to check
     * @return true if valid
     */
    fun validateEmailAddress(emailAddress: String): Boolean {
        return emailAddress.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
    }
}

/**
 * Full email message.
 */
data class Email(
    val from: EmailAddress,
    val to: List<EmailAddress>,
    val cc: List<EmailAddress> = emptyList(),
    val bcc: List<EmailAddress> = emptyList(),
    val subject: String,
    val content: EmailContent,
    val replyTo: EmailAddress? = null,
    val attachments: List<EmailAttachment> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val priority: EmailPriority = EmailPriority.NORMAL
)

/**
 * Email address with optional display name.
 */
data class EmailAddress(
    val email: String,
    val name: String? = null
) {
    override fun toString(): String {
        return if (name.isNullOrBlank()) {
            email
        } else {
            "$name <$email>"
        }
    }
}

/**
 * Plain and/or HTML body.
 */
data class EmailContent(
    val text: String? = null,
    val html: String? = null,
    val charset: String = "UTF-8"
) {
    init {
        require(!text.isNullOrBlank() || !html.isNullOrBlank()) {
            "At least one of plain text or HTML content must be provided"
        }
    }
}

/**
 * Attachment (inline or regular).
 */
data class EmailAttachment(
    val filename: String,
    val contentType: String,
    val data: ByteArray,
    val inline: Boolean = false,
    val contentId: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmailAttachment

        if (filename != other.filename) return false
        if (contentType != other.contentType) return false
        if (!data.contentEquals(other.data)) return false
        if (inline != other.inline) return false
        if (contentId != other.contentId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + inline.hashCode()
        result = 31 * result + (contentId?.hashCode() ?: 0)
        return result
    }
}

/**
 * Message priority for MIME headers.
 */
enum class EmailPriority {
    HIGH, NORMAL, LOW
}

/**
 * Result of a send attempt.
 */
sealed class EmailSendResult {
    data class Success(
        val messageId: String,
        val timestamp: Long = System.currentTimeMillis(),
        val provider: String
    ) : EmailSendResult()

    data class Failure(
        val error: EmailSendError,
        val timestamp: Long = System.currentTimeMillis(),
        val provider: String
    ) : EmailSendResult()
}

/**
 * Structured send failure.
 */
data class EmailSendError(
    val code: String,
    val message: String,
    val cause: Throwable? = null,
    val retryable: Boolean = false
)
