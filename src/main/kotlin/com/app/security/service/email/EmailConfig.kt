package com.app.security.service.email

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "email")
data class EmailProperties(
    val from: String = "noreply@example.com",
    val fromName: String = "System"
)