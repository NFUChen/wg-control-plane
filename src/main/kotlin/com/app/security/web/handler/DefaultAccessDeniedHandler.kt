package com.app.security.web.handler

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component


@Component("DefaultAccessDeniedHandler")
class DefaultAccessDeniedHandler: AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException?
    ) {
        response.status =
            HttpServletResponse.SC_FORBIDDEN // 403
        response.contentType = "application/json"
        response.writer.write("{\"error\": \"Forbidden\"}")
    }
}