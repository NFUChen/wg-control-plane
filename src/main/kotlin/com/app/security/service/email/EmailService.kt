package com.app.security.service.email

/**
 * 抽象郵件服務接口，支援多種郵件服務提供商
 * 避免被單一供應商綁定
 */
interface EmailService {
    /**
     * 發送郵件
     * @param email 郵件內容
     * @return 發送結果
     */
    fun sendEmail(email: Email): EmailSendResult

    /**
     * 批量發送郵件
     * @param emails 郵件列表
     * @return 發送結果列表
     */
    fun sendEmails(emails: List<Email>): List<EmailSendResult>

    /**
     * 驗證郵件地址格式
     * @param emailAddress 郵件地址
     * @return 是否有效
     */
    fun validateEmailAddress(emailAddress: String): Boolean {
        return emailAddress.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
    }
}

/**
 * 郵件內容
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
 * 郵件地址
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
 * 郵件內容
 */
data class EmailContent(
    val text: String? = null,
    val html: String? = null,
    val charset: String = "UTF-8"
) {
    init {
        require(!text.isNullOrBlank() || !html.isNullOrBlank()) {
            "至少需要提供純文字或 HTML 內容其中一種"
        }
    }
}

/**
 * 郵件附件
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
 * 郵件優先級
 */
enum class EmailPriority {
    HIGH, NORMAL, LOW
}

/**
 * 郵件發送結果
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
 * 郵件發送錯誤
 */
data class EmailSendError(
    val code: String,
    val message: String,
    val cause: Throwable? = null,
    val retryable: Boolean = false
)