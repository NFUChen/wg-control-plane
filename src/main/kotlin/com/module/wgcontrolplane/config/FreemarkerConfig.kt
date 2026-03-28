package com.module.wgcontrolplane.config

import freemarker.template.Configuration
import freemarker.template.TemplateExceptionHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration as SpringConfiguration

@SpringConfiguration
class FreemarkerConfig {

    @Bean("templateConfiguration")
    fun freemarkerConfiguration(): Configuration {
        return Configuration(Configuration.VERSION_2_3_32).apply {
            setClassForTemplateLoading(FreemarkerConfig::class.java, "/templates")
            defaultEncoding = "UTF-8"
            templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
            logTemplateExceptions = false
            wrapUncheckedExceptions = true
            fallbackOnNullLoopVariable = false
        }
    }
}