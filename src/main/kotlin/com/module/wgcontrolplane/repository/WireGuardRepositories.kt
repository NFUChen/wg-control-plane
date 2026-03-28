package com.module.wgcontrolplane.repository

import com.module.wgcontrolplane.model.WireGuardClient
import com.module.wgcontrolplane.model.WireGuardServer
import org.springframework.data.jpa.repository.JpaRepository
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

    @Query("SELECT s FROM WireGuardServer s WHERE s.endpoint LIKE %:domain%")
    fun findByEndpointDomain(@Param("domain") domain: String): List<WireGuardServer>

    @Query("SELECT s FROM WireGuardServer s LEFT JOIN FETCH s.clients WHERE s.id = :id")
    fun findByIdWithClients(@Param("id") id: UUID): WireGuardServer?
}

/**
 * WireGuard client repository
 */
@Repository
interface WireGuardClientRepository : JpaRepository<WireGuardClient, UUID> {

    fun findByName(name: String): WireGuardClient?

    fun findByPublicKey(publicKey: String): WireGuardClient?

    fun existsByPublicKey(publicKey: String): Boolean

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
}

