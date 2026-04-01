package com.app.service

import com.app.model.WireGuardClient

/**
 * Service interface for executing WireGuard CLI commands dynamically
 */
interface WireGuardCommandService {

    /**
     * Launch WireGuard interface
     */
    fun launchWireGuardInterface(interfaceName: String)

    /**
     * Add peer to WireGuard interface dynamically
     */
    fun addPeerToInterface(interfaceName: String, client: WireGuardClient)

    /**
     * Remove peer from WireGuard interface dynamically
     */
    fun removePeerFromInterface(interfaceName: String, publicKey: String)

    /**
     * Stop WireGuard interface
     */
    fun stopWireGuardInterface(interfaceName: String)

    /**
     * Check if WireGuard interface is currently running
     */
    fun isInterfaceRunning(interfaceName: String): Boolean
}