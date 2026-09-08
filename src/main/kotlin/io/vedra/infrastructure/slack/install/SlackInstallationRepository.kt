package io.vedra.infrastructure.slack.install

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SlackInstallationRepository : JpaRepository<SlackInstallation, String> {
    fun countByActiveTrue(): Long
}
