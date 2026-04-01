package com.app.security.web.filter


import com.app.security.service.AuthService
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.AntPathMatcher
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException


class UserJwtAuthenticationFilter(
    private val authService: AuthService,
    private val unprotectedRoutes: Iterable<String>
) : OncePerRequestFilter() {
    companion object {
        const val BEARER_PREFIX = "Bearer "
    }

    private val pathMatcher = AntPathMatcher()

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(
        request: HttpServletRequest, response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (shouldNotFilter(request)) return

        var token = ""
        val authHeader: String? = request.getHeader(HttpHeaders.AUTHORIZATION)

        val cookies: Array<Cookie>? = request.cookies

        cookies?.forEach { cookie: Cookie ->
            if (cookie.name == authService.LOGIN_KEY) token = cookie.value
        }

        if (!authHeader.isNullOrEmpty()) {
            token = authHeader.substring(BEARER_PREFIX.length)
        }

        if (token.isEmpty()) {
            filterChain.doFilter(request, response)
            return
        }

        if (!authService.isValidToken(token)) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("{\"error\": \"InvalidToken\"}")
            return
        }

        val userDetails = authService.parseUserToken(token)
        if (userDetails == null) {
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        }

        val authentication: Authentication = UsernamePasswordAuthenticationToken(
            userDetails,
            null,
            userDetails.getAuthorities()
        )
        SecurityContextHolder.getContext().authentication = authentication

        filterChain.doFilter(request, response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        return unprotectedRoutes.any { pattern ->
            pathMatcher.match(pattern, request.requestURI)
        }
    }

}