package com.app.security.service.email

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "spring.mail")
data class MailProperties(
    val host: String = "",
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    val protocol: String = "smtp",
    val testConnection: Boolean = false,
    val properties: MailPropertiesMap = MailPropertiesMap()
)

data class MailPropertiesMap(
    val mail: MailDetailProperties = MailDetailProperties()
)

data class MailDetailProperties(
    val smtp: SmtpProperties = SmtpProperties()
)

data class SmtpProperties(
    val auth: Boolean = true,
    val starttls: StarttlsProperties = StarttlsProperties()
)

data class StarttlsProperties(
    val enable: Boolean = true
)

@ConfigurationProperties(prefix = "email")
data class EmailProperties(
    val from: String,
    val fromName: String
)