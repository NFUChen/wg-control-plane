package com.app.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "ansible")
data class AnsibleConfig(
    var callback: CallbackConfig = CallbackConfig()
) {
    data class CallbackConfig(
        var host: String = "http://localhost:8080"
    )
}