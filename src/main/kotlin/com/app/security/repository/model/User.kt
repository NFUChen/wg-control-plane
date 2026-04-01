package com.app.security.repository.model

import com.app.security.UserDoesNotSetEmail
import com.app.security.UserMustEitherSetEmailOrOAuthId
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.*


enum class Role(val value: String) {
    SuperAdmin("ROLE_SUPER_ADMIN"),
    Admin("ROLE_ADMIN"),
    User("ROLE_USER"),
}
data class UserView(
    val id: UUID,
    val userId: UUID,
    val roles: Set<String>,
)


@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_users_email", columnNames = ["email"]),
        UniqueConstraint(name = "uk_users_provider_oauth_id", columnNames = ["provider", "oauth_id"])
    ]
)
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false)
    private val username: String,

    @Column(nullable = true)
    @JsonIgnore
    private var password: String?,

    @Column(nullable = true)
    val email: String?,

    @Column(name = "oauth_id", nullable = true)
    var oauthId: String?,

    @Column(nullable = false)
    val provider: String,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "system_roles", joinColumns = [JoinColumn(name = "user_id")])
    val systemRoles: MutableSet<String> = mutableSetOf(),

    @Column(name = "is_verified", nullable = false)
    var isVerified: Boolean = false,

    @Transient
    var authorities: MutableSet<String> = mutableSetOf(),

    ) : UserDetails {

    init {
        // Validate that at least one of email or oauthId is present
        if (email.isNullOrBlank() && (oauthId.isNullOrBlank())) {
            throw UserMustEitherSetEmailOrOAuthId
        }
    }

    companion object {
        const val DEFAULT_PLATFORM = "LOCAL"
    }

    fun isLocalAccount(): Boolean {
        return provider == DEFAULT_PLATFORM
    }

    fun verify() {
        this.isVerified = true
    }

    /**
     * Update user password (already encrypted)
     * @param hashedPassword Already encrypted password
     */
    fun updateHashedPassword(hashedPassword: String) {
        this.password = hashedPassword
    }

    constructor(
        name: String,
        email: String?,
        password: String,
        provider: String,
        roles: Iterable<String>,
        oauthId: String? = null
    ) : this(
        id = null,
        username = name,
        password = password,
        email = email,
        oauthId = oauthId,
        provider = provider,
        systemRoles = roles.toMutableSet()
    )
    fun mustGetEmail(): String {
        return email ?: throw UserDoesNotSetEmail
    }

    @JsonIgnore
    override fun getAuthorities(): Collection<GrantedAuthority> = (systemRoles + authorities).map { SimpleGrantedAuthority(it) }

    override fun getPassword(): String = password ?: ""

    override fun getUsername(): String = username

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = this.isVerified
}