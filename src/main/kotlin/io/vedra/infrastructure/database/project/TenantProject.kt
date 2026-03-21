package io.vedra.infrastructure.database.project

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "tenant_projects")
data class TenantProject(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    val id: UUID? = null,
    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,
    @Column(nullable = false, length = 200)
    val name: String,
    @Column(columnDefinition = "TEXT")
    val description: String? = null,
    @Column(length = 100)
    val industry: String? = null,
    @Column(name = "is_general", nullable = false)
    val isGeneral: Boolean = false,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    constructor() : this(
        tenantId = UUID.randomUUID(),
        name = "",
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TenantProject) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "TenantProject(id=$id, name=$name, industry=$industry, isGeneral=$isGeneral)"
}
