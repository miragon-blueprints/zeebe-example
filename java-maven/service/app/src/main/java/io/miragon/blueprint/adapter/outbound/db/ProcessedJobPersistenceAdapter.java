package io.miragon.blueprint.adapter.outbound.db;

import io.miragon.common.zeebe.idempotency.JobOutcome;
import io.miragon.common.zeebe.idempotency.JobOutcome.BpmnErrorThrown;
import io.miragon.common.zeebe.idempotency.JobOutcome.Completed;
import io.miragon.common.zeebe.idempotency.ProcessedJobStore;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ProcessedJobPersistenceAdapter implements ProcessedJobStore {

    private final ProcessedJobJpaRepository repository;

    private final Clock clock;

    public ProcessedJobPersistenceAdapter(ProcessedJobJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public Optional<JobOutcome> findOutcome(long jobKey) {
        return repository.findById(jobKey).flatMap(ProcessedJobPersistenceAdapter::toOutcome);
    }

    @Override
    public void claim(long jobKey) {
        repository.saveAndFlush(new ProcessedJobEntity(jobKey, LocalDateTime.now(clock), null, null, null));
    }

    @Override
    public void recordOutcome(long jobKey, JobOutcome outcome) {
        LocalDateTime processedAt = LocalDateTime.now(clock);
        ProcessedJobEntity entity = switch (outcome) {
            case Completed completed -> new ProcessedJobEntity(jobKey, processedAt, completed.variablesJson(), null, null);
            case BpmnErrorThrown error ->
                new ProcessedJobEntity(jobKey, processedAt, null, error.errorCode(), error.errorMessage());
        };
        repository.save(entity);
    }

    private static Optional<JobOutcome> toOutcome(ProcessedJobEntity entity) {
        if (entity.getVariables() != null) {
            return Optional.of(new Completed(entity.getVariables()));
        }
        if (entity.getErrorCode() != null) {
            return Optional.of(new BpmnErrorThrown(entity.getErrorCode(), entity.getErrorMessage()));
        }
        return Optional.empty();
    }
}
