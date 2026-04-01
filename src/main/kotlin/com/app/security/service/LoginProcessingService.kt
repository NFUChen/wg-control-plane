package com.app.security.service

import com.app.security.repository.model.User
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

data class LoginProcessingResult(
    val userToken: String,
    val exchangeToken: String?,
    val redirectEndpoint: String?
)

interface LoginProcessingService {
    fun processLogin(user: User): LoginProcessingResult
}

@Service
class DefaultLoginProcessingService(
    private val authService: AuthService,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : LoginProcessingService {

    override fun processLogin(user: User): LoginProcessingResult {
        // 生成用戶 token
        val userToken = authService.login(user)

        // 發布登入事件
        applicationEventPublisher.publishEvent(UserLoginEvent(user))

        return LoginProcessingResult(
            userToken = userToken,
            exchangeToken = null,
            redirectEndpoint = null
        )
    }
}