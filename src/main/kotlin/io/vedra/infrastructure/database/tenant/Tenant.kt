package io.vedra.infrastructure.database.tenant

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tenants")
data class Tenant(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    val id: UUID? = null,
    @Column(nullable = false, length = 200)
    val name: String,
    @Column(name = "slack_workspace_id", length = 50, unique = true)
    val slackWorkspaceId: String? = null,
    @Column(nullable = false)
    val active: Boolean = true,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    constructor() : this(
        name = "",
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Tenant) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "Tenant(id=$id, name=$name, slackWorkspaceId=$slackWorkspaceId)"
}
