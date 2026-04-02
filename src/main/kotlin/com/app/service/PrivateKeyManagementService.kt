package com.app.service

import com.app.view.ansible.CreatePrivateKeyRequest
import com.app.view.ansible.PrivateKeySummaryResponse
import com.app.view.ansible.UpdatePrivateKeyRequest
import java.util.*

interface PrivateKeyManagementService {

    fun listAll(): List<PrivateKeySummaryResponse>

    fun getSummary(id: UUID): PrivateKeySummaryResponse?

    fun create(request: CreatePrivateKeyRequest): PrivateKeySummaryResponse

    fun update(id: UUID, request: UpdatePrivateKeyRequest): PrivateKeySummaryResponse

    fun delete(id: UUID)

    /** Returns PEM material; use only when the client must rotate or verify the key. */
    fun getKeyContent(id: UUID): String
}
