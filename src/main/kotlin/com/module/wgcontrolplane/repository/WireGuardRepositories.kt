package com.module.wgcontrolplane.repository

import com.module.wgcontrolplane.model.WgInterface
import com.module.wgcontrolplane.model.WgPeer
import com.module.wgcontrolplane.model.WireGuardConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

/**
 * WireGuard configuration repository
 */
@Repository
interface WireGuardConfigRepository : JpaRepository<WireGuardConfig, UUID> {

    fun findByName(name: String): WireGuardConfig?

    fun existsByName(name: String): Boolean

    @Query("SELECT c FROM WireGuardConfig c WHERE c.endpoint IS NOT NULL")
    fun findAllServers(): List<WireGuardConfig>

    @Query("SELECT c FROM WireGuardConfig c WHERE c.endpoint IS NULL")
    fun findAllClients(): List<WireGuardConfig>
}

/**
 * WireGuard interface configuration repository
 */
@Repository
interface WgInterfaceRepository : JpaRepository<WgInterface, UUID> {

    fun findByConfigId(configId: UUID): WgInterface?

    @Query("SELECT i FROM WgInterface i WHERE i.listenPort = :port")
    fun findByListenPort(@Param("port") port: Int): List<WgInterface>

    fun existsByListenPort(port: Int): Boolean
}

/**
 * WireGuard Peer Repository
 */
@Repository
interface WgPeerRepository : JpaRepository<WgPeer, UUID> {

    fun findByConfigId(configId: UUID): List<WgPeer>

    fun findByPublicKey(publicKey: String): WgPeer?

    fun existsByPublicKey(publicKey: String): Boolean

    @Query("SELECT p FROM WgPeer p WHERE p.endpoint LIKE %:domain%")
    fun findByEndpointDomain(@Param("domain") domain: String): List<WgPeer>

    fun deleteByConfigId(configId: UUID)
}