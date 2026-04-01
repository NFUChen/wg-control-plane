package com.app.security.service.email

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.*

/**
 * AWS SES 郵件服務實現
 */
@Service("aws")
class AwsSesEmailService(
    private val sesClient: SesClient
) : EmailService {

    private val logger = LoggerFactory.getLogger(AwsSesEmailService::class.java)

    override fun sendEmail(email: Email): EmailSendResult {
        logger.info("Sending email to: ${email.to.map { it.email }} with subject: ${email.subject}")
        val utf8String = Charsets.UTF_8.toString()
        return try {
            val request = SendEmailRequest.builder()
                .source(email.from.toString())
                .destination(Destination.builder().toAddresses(email.to.map { it.toString() }).build())
                .message(
                    Message.builder()
                        .subject(Content.builder().data(email.subject).charset(utf8String).build())
                        .body(
                            Body.builder().apply {
                                email.content.text?.let { text ->
                                    text(Content.builder().data(text).charset(utf8String).build())
                                }
                                email.content.html?.let { html ->
                                    html(Content.builder().data(html).charset(utf8String).build())
                                }
                            }.build()
                        )
                        .build()
                )
                .build()

            val response = sesClient.sendEmail(request)
            logger.info("Email sent successfully, MessageId: ${response.messageId()}")
            EmailSendResult.Success(
                messageId = response.messageId(),
                provider = "AWS-SES"
            )
        } catch (e: Exception) {
            logger.error("Failed to send email", e)
            EmailSendResult.Failure(
                error = EmailSendError(
                    code = "SES_ERROR",
                    message = e.message ?: "Unknown error",
                    cause = e
                ),
                provider = "AWS-SES"
            )
        }
    }

    override fun sendEmails(emails: List<Email>): List<EmailSendResult> {
        return emails.map { sendEmail(it) }
    }
}