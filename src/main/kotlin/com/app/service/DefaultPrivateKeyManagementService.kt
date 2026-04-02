package com.app.service

import com.app.model.PrivateKey
import com.app.repository.AnsibleHostRepository
import com.app.repository.PrivateKeyRepository
import com.app.view.ansible.CreatePrivateKeyRequest
import com.app.view.ansible.PrivateKeySummaryResponse
import com.app.view.ansible.UpdatePrivateKeyRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class DefaultPrivateKeyManagementService(
    private val privateKeyRepository: PrivateKeyRepository,
    private val ansibleHostRepository: AnsibleHostRepository
) : PrivateKeyManagementService {

    override fun listAll(): List<PrivateKeySummaryResponse> {
        return privateKeyRepository.findAll().map { it.toSummary() }
    }

    override fun getSummary(id: UUID): PrivateKeySummaryResponse? {
        return privateKeyRepository.findById(id).map { it.toSummary() }.orElse(null)
    }

    @Transactional
    override fun create(request: CreatePrivateKeyRequest): PrivateKeySummaryResponse {
        val name = request.name.trim()
        if (name.isEmpty()) throw IllegalArgumentException("Name is required")
        if (request.content.isBlank()) throw IllegalArgumentException("Key content is required")
        if (privateKeyRepository.existsByName(name)) {
            throw IllegalArgumentException("A private key with name '$name' already exists")
        }
        val entity = PrivateKey(
            name = name,
            content = request.content.trim(),
            enabled = request.enabled,
            description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        )
        return privateKeyRepository.save(entity).toSummary()
    }

    @Transactional
    override fun update(id: UUID, request: UpdatePrivateKeyRequest): PrivateKeySummaryResponse {
        val existing = privateKeyRepository.findById(id).orElse(null)
            ?: throw NoSuchElementException("Private key $id not found")
        val name = request.name.trim()
        if (name.isEmpty()) throw IllegalArgumentException("Name is required")
        if (name != existing.name && privateKeyRepository.existsByName(name)) {
            throw IllegalArgumentException("A private key with name '$name' already exists")
        }
        val newContent = request.content?.trim()?.takeIf { it.isNotEmpty() } ?: existing.content
        val updated = existing.copy(
            name = name,
            content = newContent,
            enabled = request.enabled,
            description = request.description?.trim()?.takeIf { it.isNotEmpty() }
        )
        return privateKeyRepository.save(updated).toSummary()
    }

    @Transactional
    override fun delete(id: UUID) {
        if (!privateKeyRepository.existsById(id)) {
            throw NoSuchElementException("Private key $id not found")
        }
        val inUse = ansibleHostRepository.countHostsUsingPrivateKey(id)
        if (inUse > 0) {
            throw IllegalStateException("Cannot delete private key: $inUse Ansible host(s) still reference it")
        }
        privateKeyRepository.deleteById(id)
    }

    override fun getKeyContent(id: UUID): String {
        val key = privateKeyRepository.findById(id).orElse(null)
            ?: throw NoSuchElementException("Private key $id not found")
        return key.content
    }

    private fun PrivateKey.toSummary() = PrivateKeySummaryResponse(
        id = id,
        name = name,
        enabled = enabled,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
