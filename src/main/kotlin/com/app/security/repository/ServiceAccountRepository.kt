package com.app.security.repository

import com.app.security.repository.model.ServiceAccount
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional
import java.util.UUID

@Repository
interface ServiceAccountRepository: CrudRepository<ServiceAccount, UUID> {
    fun findByClientId(clientId: String): Optional<ServiceAccount>
    fun existsByClientId(clientId: String): Boolean
    fun findAllByEnabled(enabled: Boolean): List<ServiceAccount>
}