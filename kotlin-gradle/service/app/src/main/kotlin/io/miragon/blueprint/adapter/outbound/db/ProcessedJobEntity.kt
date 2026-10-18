package io.miragon.blueprint.adapter.outbound.db

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.time.LocalDateTime

@Entity(name = "processed_job")
data class ProcessedJobEntity(

    @Id
    @Column(name = "job_key", nullable = false)
    val jobKey: Long,

    @Column(name = "processed_at", nullable = false)
    val processedAt: LocalDateTime,

    @Column(name = "variables", columnDefinition = "text")
    val variables: String? = null,

    @Column(name = "error_code")
    val errorCode: String? = null,

    @Column(name = "error_message", columnDefinition = "text")
    val errorMessage: String? = null,
)
