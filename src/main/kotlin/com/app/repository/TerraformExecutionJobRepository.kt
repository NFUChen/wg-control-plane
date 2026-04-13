package com.app.repository

import com.app.model.TerraformExecutionJob
import com.app.model.TerraformExecutionStatus
import com.app.model.TerraformOperation
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * Repository for managing Terraform execution jobs
 */
@Repository
interface TerraformExecutionJobRepository : CrudRepository<TerraformExecutionJob, UUID> {

    fun findAll(pageable: Pageable): Page<TerraformExecutionJob>
    /**
     * Find jobs by status
     */
    fun findByStatus(status: TerraformExecutionStatus): List<TerraformExecutionJob>

    /**
     * Find jobs by status with pagination
     */
    fun findByStatus(status: TerraformExecutionStatus, pageable: Pageable): Page<TerraformExecutionJob>

    /**
     * Find jobs by operation type
     */
    fun findByOperation(operation: TerraformOperation): List<TerraformExecutionJob>

    /**
     * Find jobs by provider identifier
     */
    fun findByProviderIdentifier(providerIdentifier: String): List<TerraformExecutionJob>

    /**
     * Find jobs by operation and status
     */
    fun findByOperationAndStatus(
        operation: TerraformOperation,
        status: TerraformExecutionStatus
    ): List<TerraformExecutionJob>

    /**
     * Find running jobs
     */
    fun findByStatusIn(statuses: List<TerraformExecutionStatus>): List<TerraformExecutionJob>

    /**
     * Find jobs triggered by a specific user/system
     */
    fun findByTriggeredBy(triggeredBy: String): List<TerraformExecutionJob>

    /**
     * Find jobs created within a time range
     */
    fun findByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<TerraformExecutionJob>

    /**
     * Find jobs created within a time range with pagination
     */
    fun findByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        pageable: Pageable
    ): Page<TerraformExecutionJob>

    /**
     * Find recent jobs (most recent first)
     */
    fun findTop10ByOrderByCreatedAtDesc(): List<TerraformExecutionJob>

    /**
     * Find jobs with specific status ordered by creation time
     */
    fun findByStatusOrderByCreatedAtDesc(status: TerraformExecutionStatus): List<TerraformExecutionJob>

    /**
     * Count jobs by status
     */
    fun countByStatus(status: TerraformExecutionStatus): Long

    /**
     * Count jobs by operation
     */
    fun countByOperation(operation: TerraformOperation): Long

    /**
     * Check if there are any running jobs for a specific operation
     */
    fun existsByOperationAndStatus(
        operation: TerraformOperation,
        status: TerraformExecutionStatus
    ): Boolean

    /**
     * Find long-running jobs (running for more than specified hours)
     */
    @Query("""
        SELECT j FROM TerraformExecutionJob j
        WHERE j.status = :status
        AND j.startedAt < :cutoffTime
    """)
    fun findLongRunningJobs(
        @Param("status") status: TerraformExecutionStatus,
        @Param("cutoffTime") cutoffTime: LocalDateTime
    ): List<TerraformExecutionJob>

    /**
     * Clean up old completed jobs
     */
    @Modifying
    @Query("""
        DELETE FROM TerraformExecutionJob j
        WHERE j.status IN ('SUCCESS', 'FAILED', 'CANCELLED')
        AND j.completedAt < :cutoffDate
    """)
    fun deleteOldCompletedJobs(@Param("cutoffDate") cutoffDate: LocalDateTime): Int
}