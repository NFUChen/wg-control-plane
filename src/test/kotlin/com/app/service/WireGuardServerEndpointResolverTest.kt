package com.app.service

import com.app.model.GlobalConfig
import com.app.model.IPAddress
import com.app.model.WireGuardServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WireGuardServerEndpointResolverTest {

    @Test
    fun `formatHostPort IPv4`() {
        assertEquals(
            "203.0.113.7:51820",
            WireGuardServerEndpointResolver.formatHostPort("203.0.113.7", 51820)
        )
    }

    @Test
    fun `formatHostPort IPv6`() {
        assertEquals(
            "[2001:db8::1]:51820",
            WireGuardServerEndpointResolver.formatHostPort("2001:db8::1", 51820)
        )
    }

    @Test
    fun `formatHostPort IPv6 already bracketed`() {
        assertEquals(
            "[2001:db8::1]:51820",
            WireGuardServerEndpointResolver.formatHostPort("[2001:db8::1]", 51820)
        )
    }

    @Test
    fun `DefaultWireGuardServerEndpointResolver uses global endpoint when no ansible host`() {
        val resolver = DefaultWireGuardServerEndpointResolver()
        val server = WireGuardServer(
            name = "test",
            privateKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            publicKey = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",
            addresses = mutableListOf(IPAddress("10.0.0.1/24")),
            listenPort = 51820,
            agentToken = "test-token"
        )
        val global = GlobalConfig(serverEndpoint = "ep.example.com:51820")
        assertEquals("ep.example.com:51820", resolver.resolve(server, global))
    }
}
