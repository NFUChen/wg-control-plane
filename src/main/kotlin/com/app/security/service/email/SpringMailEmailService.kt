package com.app.security.service.email

import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.core.io.ByteArrayResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import java.util.*

/**
 * Spring Boot Mail email service implementation.
 */
@Service("springMail")
class SpringMailEmailService(
    private val javaMailSender: JavaMailSender,
    private val emailProperties: EmailProperties
) : EmailService {

    private val logger = LoggerFactory.getLogger(SpringMailEmailService::class.java)

    override fun sendEmail(email: Email): EmailSendResult {
        logger.info("Sending email to: ${email.to.map { it.email }} with subject: ${email.subject}")

        return try {
            val mimeMessage: MimeMessage = javaMailSender.createMimeMessage()
            val helper = MimeMessageHelper(mimeMessage, true, email.content.charset)

            // From
            val fromAddress = InternetAddress(
                email.from.email,
                email.from.name ?: emailProperties.fromName
            )
            helper.setFrom(fromAddress)

            // To
            val toAddresses = email.to.map {
                InternetAddress(it.email, it.name)
            }.toTypedArray()
            helper.setTo(toAddresses)

            // CC
            if (email.cc.isNotEmpty()) {
                val ccAddresses = email.cc.map {
                    InternetAddress(it.email, it.name)
                }.toTypedArray()
                helper.setCc(ccAddresses)
            }

            // BCC
            if (email.bcc.isNotEmpty()) {
                val bccAddresses = email.bcc.map {
                    InternetAddress(it.email, it.name)
                }.toTypedArray()
                helper.setBcc(bccAddresses)
            }

            // Reply-To
            email.replyTo?.let {
                helper.setReplyTo(InternetAddress(it.email, it.name))
            }

            // Subject
            helper.setSubject(email.subject)

            // Body
            when {
                email.content.html != null && email.content.text != null -> {
                    helper.setText(email.content.text, email.content.html)
                }
                email.content.html != null -> {
                    helper.setText(email.content.html, true)
                }
                email.content.text != null -> {
                    helper.setText(email.content.text, false)
                }
            }

            // Priority
            when (email.priority) {
                EmailPriority.HIGH -> {
                    helper.mimeMessage.setHeader("X-Priority", "1")
                    helper.mimeMessage.setHeader("Importance", "high")
                }
                EmailPriority.LOW -> {
                    helper.mimeMessage.setHeader("X-Priority", "5")
                    helper.mimeMessage.setHeader("Importance", "low")
                }
                else -> {
                    // NORMAL — no extra headers
                }
            }

            // Custom headers
            email.headers.forEach { (key, value) ->
                helper.mimeMessage.setHeader(key, value)
            }

            // Attachments
            email.attachments.forEach { attachment ->
                if (attachment.inline && attachment.contentId != null) {
                    val inputStreamSource = ByteArrayResource(attachment.data)
                    helper.addInline(attachment.contentId, inputStreamSource, attachment.contentType)
                } else {
                    val inputStreamSource = ByteArrayResource(attachment.data)
                    helper.addAttachment(attachment.filename, inputStreamSource, attachment.contentType)
                }
            }

            // Send
            javaMailSender.send(mimeMessage)

            val messageId = UUID.randomUUID().toString()
            logger.info("Email sent successfully, MessageId: $messageId")

            EmailSendResult.Success(
                messageId = messageId,
                provider = "Spring-Mail"
            )

        } catch (e: Exception) {
            logger.error("Failed to send email", e)
            EmailSendResult.Failure(
                error = EmailSendError(
                    code = "MAIL_ERROR",
                    message = e.message ?: "Unknown error",
                    cause = e,
                    retryable = true
                ),
                provider = "Spring-Mail"
            )
        }
    }

    override fun sendEmails(emails: List<Email>): List<EmailSendResult> {
        return emails.map { sendEmail(it) }
    }
}