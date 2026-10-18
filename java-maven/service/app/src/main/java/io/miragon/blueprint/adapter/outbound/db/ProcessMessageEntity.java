package io.miragon.blueprint.adapter.outbound.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity(name = "process_message_outbox")
public class ProcessMessageEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "message_name", nullable = false)
    private String messageName;

    @Column(name = "correlation_key", nullable = false)
    private String correlationKey;

    @Column(name = "variables", nullable = false, columnDefinition = "text")
    private String variables;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ProcessMessageEntity() {
    }

    public ProcessMessageEntity(
        UUID id,
        String messageName,
        String correlationKey,
        String variables,
        LocalDateTime createdAt
    ) {
        this.id = id;
        this.messageName = messageName;
        this.correlationKey = correlationKey;
        this.variables = variables;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getMessageName() {
        return messageName;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public String getVariables() {
        return variables;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
