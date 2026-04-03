package com.app.service

import com.app.model.GlobalConfig
import com.app.model.WireGuardServer
import com.app.repository.AnsibleHostRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Optional
import java.util.UUID

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
    fun `DefaultWireGuardServerEndpointResolver falls back to global endpoint when ansible host row is missing`() {
        val hostId = UUID.fromString("1cc0d6be-f8e4-42e2-905a-f7351b74e0de")
        val repo = mock(AnsibleHostRepository::class.java)
        `when`(repo.findById(hostId)).thenReturn(Optional.empty())
        val resolver = DefaultWireGuardServerEndpointResolver(repo)
        val server = mock(WireGuardServer::class.java)
        `when`(server.hostId).thenReturn(hostId)
        val global = GlobalConfig(serverEndpoint = "ep.example.com:51820")
        assertEquals("ep.example.com:51820", resolver.resolve(server, global))
    }
}
