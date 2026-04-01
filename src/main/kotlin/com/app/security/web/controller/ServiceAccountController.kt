package com.app.security.web.controller

import com.app.security.repository.model.ServiceAccount
import com.app.security.service.ServiceAccountAuthRequest
import com.app.security.service.ServiceAccountCreationResponse
import com.app.security.service.ServiceAccountManager
import com.app.security.service.ServiceAccountRegistration
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*


data class TokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer"
)


@RestController
@RequestMapping("/api/public/service-accounts")
class PublicServiceController(
    private val serviceAccountManager: ServiceAccountManager
) {
    /**
     * Service Account 認證並獲取 Access Token
     */
    @PostMapping("/auth")
    fun authenticate(
        @Valid @RequestBody request: ServiceAccountAuthRequest
    ): TokenResponse {
        val serviceAccount = serviceAccountManager.authenticate(request)
        val accessToken = serviceAccountManager.generateAccessToken(serviceAccount)

        return TokenResponse(accessToken = accessToken)
    }
}

@RestController
@RequestMapping("/api/private/service-accounts")
class ServiceAccountController(
    private val serviceAccountManager: ServiceAccountManager
) {



    /**
     * 創建新的 Service Account
     * 系統會自動生成 client secret，僅在此回應中顯示一次
     * 需要管理員權限
     */
    @PostMapping("/create")
    fun createServiceAccount(
        @Valid @RequestBody request: ServiceAccountRegistration
    ): ResponseEntity<ServiceAccountCreationResponse> {
        val creationResponse = serviceAccountManager.createServiceAccount(request)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(creationResponse)
    }

    /**
     * 根據 Client ID 獲取 Service Account 資訊
     * 需要管理員權限
     */
    @GetMapping("/{clientId}")
    fun getServiceAccount(
        @PathVariable clientId: String
    ): ServiceAccount {
        return serviceAccountManager.getServiceAccountByClientId(clientId)
    }

    /**
     * 獲取 Service Account 的權限範圍
     * 用於 JWT 驗證後查詢權限
     */
    @GetMapping("/{clientId}/scopes")
    fun getServiceAccountScopes(
        @PathVariable clientId: String
    ): Map<String, Set<String>> {
        val scopes = serviceAccountManager.getScopes(clientId)
        return mapOf("scopes" to scopes)
    }

    /**
     * 啟用 Service Account
     * 需要管理員權限
     */
    @PutMapping("/{clientId}/enable")
    fun enableServiceAccount(
        @PathVariable clientId: String
    ): Map<String, String> {
        serviceAccountManager.enableServiceAccount(clientId)
        return mapOf("message" to "Service Account enabled")
    }

    /**
     * 停用 Service Account
     * 需要管理員權限
     */
    @PutMapping("/{clientId}/disable")
    fun disableServiceAccount(
        @PathVariable clientId: String
    ): Map<String, String> {
        serviceAccountManager.disableServiceAccount(clientId)
        return mapOf("message" to "Service Account disabled")
    }

    /**
     * 刪除 Service Account
     * 需要管理員權限
     */
    @DeleteMapping("/{clientId}")
    fun deleteServiceAccount(
        @PathVariable clientId: String
    ): Map<String, String> {
        serviceAccountManager.deleteServiceAccount(clientId)
        return mapOf("message" to "Service Account deleted")
    }
}
