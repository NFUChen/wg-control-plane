package com.app.repository

import com.app.model.ClientDeploymentStatus
import com.app.model.WireGuardClient
import com.app.model.WireGuardServer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

/**
 * WireGuard server repository
 */
@Repository
interface WireGuardServerRepository : JpaRepository<WireGuardServer, UUID> {

    fun findByName(name: String): WireGuardServer?

    fun existsByName(name: String): Boolean

    fun findByEnabledTrue(): List<WireGuardServer>

    @Query("SELECT s FROM WireGuardServer s WHERE s.listenPort = :port")
    fun findByListenPort(@Param("port") port: Int): List<WireGuardServer>

    fun existsByListenPort(port: Int): Boolean

    fun existsByInterfaceName(interfaceName: String): Boolean

    @Query("SELECT s FROM WireGuardServer s LEFT JOIN FETCH s.clients WHERE s.id = :id")
    fun findByIdWithClients(@Param("id") id: UUID): WireGuardServer?

    /** Clear dangling references when an Ansible host row is removed (no DB FK on this column). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE WireGuardServer s SET s.ansibleHost = null WHERE s.ansibleHost.id = :hostId")
    fun clearAnsibleHostReference(@Param("hostId") hostId: UUID): Int
}

/**
 * WireGuard client repository
 */
@Repository
interface WireGuardClientRepository : JpaRepository<WireGuardClient, UUID> {

    fun findByName(name: String): WireGuardClient?

    fun findByServerId(serverId: UUID): List<WireGuardClient>

    fun findByServerIdAndEnabledTrue(serverId: UUID): List<WireGuardClient>

    fun findByEnabledTrue(): List<WireGuardClient>

    @Query("SELECT c FROM WireGuardClient c WHERE c.server.id = :serverId AND c.enabled = true")
    fun findActiveClientsByServerId(@Param("serverId") serverId: UUID): List<WireGuardClient>

    @Query("SELECT c FROM WireGuardClient c WHERE c.lastHandshake > :since")
    fun findRecentlyActiveClients(@Param("since") since: LocalDateTime): List<WireGuardClient>

    @Query("SELECT COUNT(c) FROM WireGuardClient c WHERE c.server.id = :serverId AND c.enabled = true")
    fun countActiveClientsByServerId(@Param("serverId") serverId: UUID): Long

    fun deleteByServerId(serverId: UUID)

    fun existsByAnsibleHostIdAndInterfaceName(hostId: UUID, interfaceName: String): Boolean

    fun existsByAnsibleHostIdAndInterfaceNameAndIdNot(hostId: UUID, interfaceName: String, id: UUID): Boolean

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        "UPDATE WireGuardClient c SET c.ansibleHost = null, c.deploymentStatus = :noneStatus WHERE c.ansibleHost.id = :hostId",
    )
    fun clearAnsibleHostReference(
        @Param("hostId") hostId: UUID,
        @Param("noneStatus") noneStatus: ClientDeploymentStatus,
    ): Int

    fun findByAgentToken(token: String): WireGuardClient?
}

