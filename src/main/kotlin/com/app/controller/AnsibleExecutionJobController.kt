package com.app.controller

import com.app.model.AnsibleExecutionStatus
import com.app.repository.AnsibleExecutionJobRepository
import com.app.view.ansible.AnsibleExecutionJobDetailResponse
import com.app.view.ansible.AnsibleExecutionJobSummaryResponse
import com.app.view.ansible.toDetailResponse
import com.app.view.ansible.toSummaryResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

/**
 * Read-only REST API for Ansible execution job history.
 */
@RestController
@RequestMapping("/api/private/ansible/execution-jobs")
class AnsibleExecutionJobController(
    private val executionJobRepository: AnsibleExecutionJobRepository,
) {

    @GetMapping
    fun listJobs(
        @RequestParam(required = false) status: AnsibleExecutionStatus?,
        @PageableDefault(size = 50, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): ResponseEntity<Page<AnsibleExecutionJobSummaryResponse>> {
        val page = when (status) {
            null -> executionJobRepository.findAll(pageable)
            else -> executionJobRepository.findByStatus(status, pageable)
        }
        return ResponseEntity.ok(page.map { job -> job.toSummaryResponse() })
    }

    @GetMapping("/{id}")
    fun getJob(@PathVariable id: UUID): ResponseEntity<AnsibleExecutionJobDetailResponse> {
        val job = executionJobRepository.findById(id).orElse(null)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(job.toDetailResponse())
    }
}
