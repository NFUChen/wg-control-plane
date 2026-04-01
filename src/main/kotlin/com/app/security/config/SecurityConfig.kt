package com.app.security.config

import com.app.security.repository.model.Role
import com.app.security.service.AuthService
import com.app.security.web.filter.UserJwtAuthenticationFilter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpMethod
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.firewall.HttpFirewall
import org.springframework.security.web.firewall.StrictHttpFirewall
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping


@Configuration
class SpringSecurityConfig(
    val webProperties: WebProperties,
    @Lazy val authService: AuthService,
    @Qualifier("DefaultAccessDeniedHandler") val accessDeniedHandler: AccessDeniedHandler,
    val requestMappingHandlerMapping: RequestMappingHandlerMapping,
) {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
    ): SecurityFilterChain {
        http
            .exceptionHandling { it.accessDeniedHandler(accessDeniedHandler) }
            .sessionManagement { sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .addFilterBefore(userJwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter::class.java)
            .cors { cors -> cors.configurationSource(withDefaultCorsConfigurationSource()) }
            .authorizeHttpRequests(withDefaultChain())
            .csrf { it.disable() }
        return http.build()
    }

    @Bean
    fun httpFirewall(): HttpFirewall {
        val defaultHttpFirewall = StrictHttpFirewall()
        defaultHttpFirewall.setAllowUrlEncodedSlash(true)
        defaultHttpFirewall.setAllowBackSlash((true))
        defaultHttpFirewall.setAllowSemicolon(true)
        return defaultHttpFirewall
    }

    fun withDefaultChain(): Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> {
        return Customizer { auth ->
            auth
                .requestMatchers(*webProperties.unprotectedRoutes.toTypedArray()).permitAll()
                .requestMatchers(*spaPublicPathMatchers()).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
        }
    }

    /**
     * Paths that serve the SPA shell (HTML + hashed assets under the same prefix as Angular `baseHref`).
     * Kept in code alongside [WebProperties.spaBasePath] so `unprotected-routes` does not list every client route.
     */
    private fun spaPublicPathMatchers(): Array<String> {
        val base = webProperties.spaBasePath.trimEnd('/')
        return arrayOf("/", base, "$base/**")
    }

    fun userJwtAuthenticationFilter(): UserJwtAuthenticationFilter {
        val merged = webProperties.unprotectedRoutes + spaPublicPathMatchers().toList()
        return UserJwtAuthenticationFilter(authService, merged)
    }

    fun withDefaultCorsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOriginPatterns = listOf("*")
        config.allowedMethods = listOf("*")
        config.allowCredentials = true;
        config.allowedHeaders = listOf("*")
        config.maxAge = 3600L;
        val source = UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    fun defaultPasswordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Bean
    fun roleHierarchy(): RoleHierarchy {
        val hierarchy = RoleHierarchyImpl()
        val hierarchyChain = Role.entries.joinToString(" > ", transform = { it.value })
        hierarchy.setHierarchy(hierarchyChain)
        return hierarchy
    }

    // and, if using pre-post method security also add
    @Bean
    fun methodSecurityExpressionHandler(roleHierarchy: RoleHierarchy): MethodSecurityExpressionHandler {
        val expressionHandler = DefaultMethodSecurityExpressionHandler()
        expressionHandler.setRoleHierarchy(roleHierarchy)
        return expressionHandler
    }


}