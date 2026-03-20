package io.vedra.infrastructure.database.project

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "channel_projects")
data class ChannelProject(
    @Id
    @Column(name = "channel_id", nullable = false, length = 50)
    val channelId: String,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    val project: TenantProject,
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) {
    constructor() : this(
        channelId = "",
        project = TenantProject(),
    )

    val projectId: java.util.UUID get() = project.id!!

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChannelProject) return false
        return channelId == other.channelId
    }

    override fun hashCode(): Int = channelId.hashCode()

    override fun toString(): String = "ChannelProject(channelId=$channelId, projectId=$projectId)"
}
