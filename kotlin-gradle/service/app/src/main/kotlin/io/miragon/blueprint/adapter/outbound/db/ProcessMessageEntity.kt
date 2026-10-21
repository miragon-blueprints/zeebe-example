package io.miragon.blueprint.adapter.outbound.db

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.time.LocalDateTime
import java.util.UUID

@Entity(name = "process_message_outbox")
data class ProcessMessageEntity(

    @Id
    @Column(name = "id", nullable = false)
    val id: UUID,

    @Column(name = "message_name", nullable = false)
    val messageName: String,

    @Column(name = "correlation_key", nullable = false)
    val correlationKey: String,

    @Column(name = "variables", nullable = false, columnDefinition = "text")
    val variables: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,
)
