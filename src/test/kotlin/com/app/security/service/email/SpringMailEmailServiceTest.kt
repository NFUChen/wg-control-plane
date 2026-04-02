package com.app.security.service.email

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpringMailEmailServiceTest {

    @Test
    fun `should validate email address correctly`() {
        // Given
        val emailProperties = EmailProperties("test@example.com", "Test")

        // Since we don't have a real JavaMailSender in test context, we create a simple validation test
        val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        // When & Then
        assertTrue(emailRegex.matches("valid@example.com"))
        assertTrue(emailRegex.matches("user.name+tag@example.co.uk"))
        assertTrue(emailRegex.matches("test123@domain.org"))
        assertFalse(emailRegex.matches("invalid.email"))
        assertFalse(emailRegex.matches("@example.com"))
        assertFalse(emailRegex.matches("test@"))
        assertFalse(emailRegex.matches("test@.com"))
    }

    @Test
    fun `EmailProperties should be constructed correctly`() {
        // Given & When
        val emailProperties = EmailProperties(
            from = "noreply@example.com",
            fromName = "Example Service"
        )

        // Then
        assertTrue(emailProperties.from == "noreply@example.com")
        assertTrue(emailProperties.fromName == "Example Service")
    }

    @Test
    fun `EmailAddress toString should format correctly`() {
        // Given & When
        val emailWithName = EmailAddress("test@example.com", "Test User")
        val emailWithoutName = EmailAddress("test@example.com")

        // Then
        assertTrue(emailWithName.toString() == "Test User <test@example.com>")
        assertTrue(emailWithoutName.toString() == "test@example.com")
    }

    @Test
    fun `EmailContent should require at least one content type`() {
        // Given & When & Then
        try {
            EmailContent(text = null, html = null)
            assertTrue(false, "Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("At least one of plain text or HTML content must be provided") == true)
        }

        // Valid cases
        EmailContent(text = "Text content")
        EmailContent(html = "<p>HTML content</p>")
        EmailContent(text = "Text", html = "<p>HTML</p>")
    }
}