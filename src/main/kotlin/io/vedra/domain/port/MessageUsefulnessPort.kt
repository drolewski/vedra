package io.vedra.domain.port

import io.vedra.domain.model.MessageUsefulnessResult
import io.vedra.domain.model.TenantProject

interface MessageUsefulnessPort {
    fun evaluate(
        content: String,
        project: TenantProject,
        userId: String,
    ): MessageUsefulnessResult?
}
