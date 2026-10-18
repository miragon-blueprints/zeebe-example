package io.miragon.blueprint.adapter.outbound.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity(name = "processed_job")
public class ProcessedJobEntity {

    @Id
    @Column(name = "job_key", nullable = false)
    private Long jobKey;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "variables", columnDefinition = "text")
    private String variables;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected ProcessedJobEntity() {
    }

    public ProcessedJobEntity(
        Long jobKey,
        LocalDateTime processedAt,
        String variables,
        String errorCode,
        String errorMessage
    ) {
        this.jobKey = jobKey;
        this.processedAt = processedAt;
        this.variables = variables;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getVariables() {
        return variables;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
