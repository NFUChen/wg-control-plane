package com.app.security.config

import com.app.security.web.filter.ApiKeyAuthenticationInterceptor
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import kotlin.jvm.JvmSuppressWildcards
import org.springframework.web.servlet.resource.ResourceResolver
import org.springframework.web.servlet.resource.ResourceResolverChain

@Configuration
class WebMvcConfig(
    private val apiKeyAuthenticationInterceptor: ApiKeyAuthenticationInterceptor,
    private val webProperties: WebProperties,
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(apiKeyAuthenticationInterceptor)
            .addPathPatterns("/api/internal/**")
            .order(1)
    }

    // Angular: classpath:/static/app/ — pattern /app/** is more specific than Boot's /** so static wins only under /app/.
    // Do not call registry.setOrder(HIGHEST_PRECEDENCE): that makes all static resources win over @RequestMapping and breaks /api/**, /v3/api-docs/**, Swagger UI, etc.
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val base = webProperties.spaBasePath.trimEnd('/')
        registry.addResourceHandler("$base/**")
            .addResourceLocations("classpath:/static$base/")
            .resourceChain(true)
            .addResolver(SpaIndexFallbackResolver())
    }

    private class SpaIndexFallbackResolver : ResourceResolver {
        override fun resolveResource(
            request: HttpServletRequest?,
            requestPath: String,
            locations: List<@JvmSuppressWildcards Resource>,
            chain: ResourceResolverChain,
        ): Resource? {
            return chain.resolveResource(request, requestPath, locations)
                ?: chain.resolveResource(request, "index.html", locations)
        }

        override fun resolveUrlPath(
            resourcePath: String,
            locations: List<@JvmSuppressWildcards Resource>,
            chain: ResourceResolverChain,
        ): String? = chain.resolveUrlPath(resourcePath, locations)
    }
}