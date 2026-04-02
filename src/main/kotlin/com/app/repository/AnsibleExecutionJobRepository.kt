package com.app.repository

import com.app.model.AnsibleExecutionJob
import com.app.model.AnsibleExecutionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * Repository for managing Ansible execution jobs
 */
@Repository
interface AnsibleExecutionJobRepository : JpaRepository<AnsibleExecutionJob, UUID> {

    /**
     * Find jobs by status
     */
    fun findByStatus(status: AnsibleExecutionStatus): List<AnsibleExecutionJob>

    /**
     * Find jobs by status with pagination
     */
    fun findByStatus(status: AnsibleExecutionStatus, pageable: Pageable): Page<AnsibleExecutionJob>

    /**
     * Find jobs by playbook name
     */
    fun findByPlaybook(playbook: String): List<AnsibleExecutionJob>

    /**
     * Find jobs by playbook name with pagination
     */
    fun findByPlaybook(playbook: String, pageable: Pageable): Page<AnsibleExecutionJob>

    /**
     * Find jobs by playbook and time range with pagination
     */
    fun findByPlaybookAndCreatedAtBetween(
        playbook: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        pageable: Pageable
    ): Page<AnsibleExecutionJob>

    /**
     * Find jobs by playbook and status
     */
    fun findByPlaybookAndStatus(
        playbook: String,
        status: AnsibleExecutionStatus
    ): List<AnsibleExecutionJob>

    /**
     * Find running jobs
     */
    fun findByStatusIn(statuses: List<AnsibleExecutionStatus>): List<AnsibleExecutionJob>

    /**
     * Find jobs triggered by a specific user/system
     */
    fun findByTriggeredBy(triggeredBy: String): List<AnsibleExecutionJob>

    /**
     * Find jobs created within a time range
     */
    fun findByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<AnsibleExecutionJob>

    /**
     * Find jobs created within a time range with pagination
     */
    fun findByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        pageable: Pageable
    ): Page<AnsibleExecutionJob>

    /**
     * Find jobs that completed within a time range
     */
    fun findByCompletedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): List<AnsibleExecutionJob>

    /**
     * Find recent jobs (most recent first)
     */
    fun findTop10ByOrderByCreatedAtDesc(): List<AnsibleExecutionJob>

    /**
     * Find jobs with specific status ordered by creation time
     */
    fun findByStatusOrderByCreatedAtDesc(status: AnsibleExecutionStatus): List<AnsibleExecutionJob>

    /**
     * Count jobs by status
     */
    fun countByStatus(status: AnsibleExecutionStatus): Long

    /**
     * Count jobs by playbook
     */
    fun countByPlaybook(playbook: String): Long

    /**
     * Check if there are any running jobs for a specific playbook
     */
    fun existsByPlaybookAndStatus(
        playbook: String,
        status: AnsibleExecutionStatus
    ): Boolean

    /**
     * Find long-running jobs (running for more than specified hours)
     */
    @Query("""
        SELECT j FROM AnsibleExecutionJob j
        WHERE j.status = :status
        AND j.startedAt < :cutoffTime
    """)
    fun findLongRunningJobs(
        @Param("status") status: AnsibleExecutionStatus,
        @Param("cutoffTime") cutoffTime: LocalDateTime
    ): List<AnsibleExecutionJob>

    /**
     * Find failed jobs that can be retried (failed within last X hours)
     */
    @Query("""
        SELECT j FROM AnsibleExecutionJob j
        WHERE j.status = 'FAILED'
        AND j.completedAt > :cutoffTime
        ORDER BY j.completedAt DESC
    """)
    fun findRecentFailedJobs(@Param("cutoffTime") cutoffTime: LocalDateTime): List<AnsibleExecutionJob>

    /**
     * Clean up old completed jobs
     */
    @Modifying
    @Query("""
        DELETE FROM AnsibleExecutionJob j
        WHERE j.status IN ('SUCCESS', 'FAILED', 'CANCELLED', 'TIMEOUT')
        AND j.completedAt < :cutoffDate
    """)
    fun deleteOldCompletedJobs(@Param("cutoffDate") cutoffDate: LocalDateTime): Int

    /**
     * Get playbook usage statistics
     */
    @Query("""
        SELECT
            j.playbook as playbook,
            COUNT(j) as executionCount,
            AVG(j.durationSeconds) as averageDuration,
            MAX(j.createdAt) as lastExecuted
        FROM AnsibleExecutionJob j
        WHERE j.createdAt > :since
        GROUP BY j.playbook
        ORDER BY executionCount DESC
    """)
    fun getPlaybookUsageStatistics(@Param("since") since: LocalDateTime): List<PlaybookStatistic>
}

/**
 * Projection interface for execution statistics
 */
interface ExecutionStatistic {
    val status: AnsibleExecutionStatus
    val count: Long
    val averageDuration: Double?
    val totalSuccessfulHosts: Long
    val totalFailedHosts: Long
}

/**
 * Projection interface for playbook statistics
 */
interface PlaybookStatistic {
    val playbook: String
    val executionCount: Long
    val averageDuration: Double?
    val lastExecuted: LocalDateTime
}