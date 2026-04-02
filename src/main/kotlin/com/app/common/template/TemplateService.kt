package com.app.common.template

import freemarker.template.Configuration
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.StringWriter

/**
 * Shared FreeMarker template processing for WireGuard configs and email HTML.
 */
interface TemplateService {
    /**
     * Render a template file under the configured templates directory.
     *
     * @param templatePath Path relative to the templates root
     * @param variables Variable map for the model
     * @return Rendered string
     */
    fun processTemplate(templatePath: String, variables: Map<String, Any>): String

    /**
     * Render inline template content.
     *
     * @param templateContent Template source string
     * @param variables Variable map
     * @param templateName Logical name for error messages
     * @return Rendered string
     */
    fun processInlineTemplate(templateContent: String, variables: Map<String, Any>, templateName: String = "inline"): String
}

@Service
class FreeMarkerTemplateService(
    @Qualifier("templateConfiguration") private val freemarkerConfig: Configuration
) : TemplateService {

    override fun processTemplate(templatePath: String, variables: Map<String, Any>): String {
        return try {
            val template = freemarkerConfig.getTemplate(templatePath)
            val out = StringWriter()
            template.process(variables, out)
            out.toString()
        } catch (e: Exception) {
            throw TemplateProcessingException("Failed to process template: $templatePath", e)
        }
    }

    override fun processInlineTemplate(templateContent: String, variables: Map<String, Any>, templateName: String): String {
        return try {
            val template = freemarker.template.Template(
                templateName,
                templateContent,

                freemarkerConfig
            )
            val out = StringWriter()
            template.process(variables, out)
            out.toString()
        } catch (e: Exception) {
            throw TemplateProcessingException("Failed to process inline template: $templateName", e)
        }
    }
}

class TemplateProcessingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)