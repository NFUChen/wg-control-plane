package com.module.wgcontrolplane.dto

import java.util.*

/**
 * Server statistics response with type safety
 */
data class ServerStatisticsResponse(
    val serverId: UUID,
    val serverName: String,
    val endpoint: String,
    val listenPort: Int,
    val networkAddress: String,
    val totalClients: Int,
    val onlineClients: Int,
    val offlineClients: Int,
    val totalDataReceived: Long,
    val totalDataSent: Long
)