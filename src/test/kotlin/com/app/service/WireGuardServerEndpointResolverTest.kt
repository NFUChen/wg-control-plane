package com.app.service

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
}
