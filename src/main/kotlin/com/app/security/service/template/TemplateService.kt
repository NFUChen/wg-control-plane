package com.app.security.service.template

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.io.IOException

interface TemplateService {
    /**
     * 處理模板文件，將變數替換為實際值
     *
     * @param templatePath 模板文件路徑 (相對於 resources 目錄)
     * @param variables 變數映射表
     * @return 處理後的內容
     */
    fun processTemplate(templatePath: String, variables: Map<String, String>): String
}

@Service
class DefaultTemplateService : TemplateService {

    override fun processTemplate(templatePath: String, variables: Map<String, String>): String {
        return try {
            val template = loadTemplate(templatePath)
            replaceVariables(template, variables)
        } catch (e: IOException) {
            throw TemplateProcessingException("Failed to load template: $templatePath", e)
        } catch (e: Exception) {
            throw TemplateProcessingException("Failed to process template: $templatePath", e)
        }
    }

    private fun loadTemplate(templatePath: String): String {
        val resource = ClassPathResource(templatePath)
        if (!resource.exists()) {
            throw TemplateNotFoundException("Template not found: $templatePath")
        }
        return resource.inputStream.bufferedReader().use { it.readText() }
    }

    private fun replaceVariables(template: String, variables: Map<String, String>): String {
        var processedTemplate = template

        variables.forEach { (key, value) ->
            // 支持 {{variable}} 語法
            val placeholder = "{{$key}}"
            processedTemplate = processedTemplate.replace(placeholder, value)
        }

        return processedTemplate
    }
}

class TemplateProcessingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
class TemplateNotFoundException(message: String) : RuntimeException(message)