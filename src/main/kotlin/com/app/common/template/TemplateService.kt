package com.app.common.template

import freemarker.template.Configuration
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.io.StringWriter

/**
 * 統一的 FreeMarker 模板服務
 * 支援 WireGuard 配置和郵件模板處理
 */
interface TemplateService {
    /**
     * 處理模板文件，將變數替換為實際值
     *
     * @param templatePath 模板文件路徑 (相對於 templates 目錄)
     * @param variables 變數映射表
     * @return 處理後的內容
     */
    fun processTemplate(templatePath: String, variables: Map<String, Any>): String

    /**
     * 使用內聯模板內容處理變數
     *
     * @param templateContent 模板內容字串
     * @param variables 變數映射表
     * @param templateName 模板名稱（用於錯誤追蹤）
     * @return 處理後的內容
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