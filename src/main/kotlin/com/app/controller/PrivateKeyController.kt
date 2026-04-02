package com.app.controller

import com.app.service.PrivateKeyManagementService
import com.app.view.ansible.CreatePrivateKeyRequest
import com.app.view.ansible.PrivateKeySummaryResponse
import com.app.view.ansible.UpdatePrivateKeyRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.util.*

@RestController
@RequestMapping("/api/private/ansible/private-keys")
class PrivateKeyController(
    private val privateKeyManagementService: PrivateKeyManagementService
) {

    @GetMapping
    fun list(): ResponseEntity<List<PrivateKeySummaryResponse>> {
        return ResponseEntity.ok(privateKeyManagementService.listAll())
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ResponseEntity<PrivateKeySummaryResponse> {
        val summary = privateKeyManagementService.getSummary(id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Private key not found")
        return ResponseEntity.ok(summary)
    }

    @GetMapping("/{id}/content", produces = ["text/plain"])
    fun getContent(@PathVariable id: UUID): ResponseEntity<String> {
        return try {
            ResponseEntity.ok(privateKeyManagementService.getKeyContent(id))
        } catch (e: NoSuchElementException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
        }
    }

    @PostMapping
    fun create(@RequestBody request: CreatePrivateKeyRequest): ResponseEntity<PrivateKeySummaryResponse> {
        return try {
            val created = privateKeyManagementService.create(request)
            ResponseEntity.status(HttpStatus.CREATED).body(created)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
    }

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestBody request: UpdatePrivateKeyRequest
    ): ResponseEntity<PrivateKeySummaryResponse> {
        return try {
            ResponseEntity.ok(privateKeyManagementService.update(id, request))
        } catch (e: NoSuchElementException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
        return try {
            privateKeyManagementService.delete(id)
            ResponseEntity.noContent().build()
        } catch (e: NoSuchElementException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, e.message)
        } catch (e: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, e.message)
        }
    }
}
