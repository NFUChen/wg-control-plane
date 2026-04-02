package com.app.security.repository.model

import com.app.converter.StringSetConverter
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*


@Entity
@Table(name = "service_accounts")
class ServiceAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, unique = true)
    val clientId: String,

    @Column(nullable = false)
    @JsonIgnore
    val clientSecretHash: String, // stored hashed (e.g. bcrypt)

    @Column(nullable = false)
    val name: String, // e.g. "LINE Sync Service", for human-readability

    @Column(nullable = true)
    val description: String? = null,

    @Convert(converter = StringSetConverter::class)
    @Column(name = "scopes", columnDefinition = "text")
    val scopes: Set<String> = emptySet(),

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = true)
    var lastUsedAt: LocalDateTime? = null,
)