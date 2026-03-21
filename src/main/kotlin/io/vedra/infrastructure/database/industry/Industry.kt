package io.vedra.infrastructure.database.industry

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "industries",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["tenant_id", "name"]),
    ],
)
data class Industry(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    val id: UUID? = null,
    @Column(name = "tenant_id", nullable = false, updatable = false)
    val tenantId: UUID,
    @Column(nullable = false, length = 100)
    val name: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    constructor() : this(
        tenantId = UUID.randomUUID(),
        name = "",
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Industry) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "Industry(id=$id, name=$name)"
}
