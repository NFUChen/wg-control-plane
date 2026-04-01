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
 * Spring Boot Mail 郵件服務實現
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

            // 設置發件人
            val fromAddress = InternetAddress(
                email.from.email,
                email.from.name ?: emailProperties.fromName
            )
            helper.setFrom(fromAddress)

            // 設置收件人
            val toAddresses = email.to.map {
                InternetAddress(it.email, it.name)
            }.toTypedArray()
            helper.setTo(toAddresses)

            // 設置副本收件人
            if (email.cc.isNotEmpty()) {
                val ccAddresses = email.cc.map {
                    InternetAddress(it.email, it.name)
                }.toTypedArray()
                helper.setCc(ccAddresses)
            }

            // 設置密件副本收件人
            if (email.bcc.isNotEmpty()) {
                val bccAddresses = email.bcc.map {
                    InternetAddress(it.email, it.name)
                }.toTypedArray()
                helper.setBcc(bccAddresses)
            }

            // 設置回覆地址
            email.replyTo?.let {
                helper.setReplyTo(InternetAddress(it.email, it.name))
            }

            // 設置主題
            helper.setSubject(email.subject)

            // 設置郵件內容
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

            // 設置優先級
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
                    // NORMAL - 不設置特殊標頭
                }
            }

            // 設置自定義標頭
            email.headers.forEach { (key, value) ->
                helper.mimeMessage.setHeader(key, value)
            }

            // 添加附件
            email.attachments.forEach { attachment ->
                if (attachment.inline && attachment.contentId != null) {
                    val inputStreamSource = ByteArrayResource(attachment.data)
                    helper.addInline(attachment.contentId, inputStreamSource, attachment.contentType)
                } else {
                    val inputStreamSource = ByteArrayResource(attachment.data)
                    helper.addAttachment(attachment.filename, inputStreamSource, attachment.contentType)
                }
            }

            // 發送郵件
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