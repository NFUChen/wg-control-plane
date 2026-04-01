package com.app.security.service

import com.app.security.config.AdminProperties
import com.app.security.repository.model.Role
import jakarta.annotation.PostConstruct
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component


@Component
class SystemInitService(
    private val adminProperties: AdminProperties,
    private val defaultRegistrationService: DefaultRegistrationService
) {

    private val logger = LoggerFactory.getLogger(SystemInitService::class.java)

    @PostConstruct
    @Transactional
    fun init() {
        // Initialize the system, e.g., create default admin user if it doesn't exist
        this.initSystemUser()
    }
    /**
     * Initializes the system user with admin privileges.
     * This method is called during application startup to ensure that
     * there is always an admin user available.
     */
    fun initSystemUser() {
        logger.info("Initializing system user with admin privileges...")
        this.defaultRegistrationService.registerUser(
            UserRegistrationRequest(
                username = adminProperties.username,
                password = adminProperties.password,
                email = adminProperties.email,
                roles = setOf(Role.Admin.value),
                isUpsert = true
            )
        )
    }
}