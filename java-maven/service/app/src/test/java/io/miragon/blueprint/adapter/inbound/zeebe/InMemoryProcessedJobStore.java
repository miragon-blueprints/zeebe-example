package io.miragon.blueprint.adapter.inbound.zeebe;

import io.camunda.client.impl.CamundaObjectMapper;
import io.miragon.common.zeebe.idempotency.IdempotentJobExecutor;
import io.miragon.common.zeebe.idempotency.JobOutcome;
import io.miragon.common.zeebe.idempotency.ProcessedJobStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.transaction.support.TransactionOperations;

class InMemoryProcessedJobStore implements ProcessedJobStore {

    private final Map<Long, JobOutcome> outcomes = new HashMap<>();

    static IdempotentJobExecutor inMemoryIdempotentJobExecutor() {
        return new IdempotentJobExecutor(
            new InMemoryProcessedJobStore(), TransactionOperations.withoutTransaction(), new CamundaObjectMapper());
    }

    @Override
    public Optional<JobOutcome> findOutcome(long jobKey) {
        return Optional.ofNullable(outcomes.get(jobKey));
    }

    @Override
    public void claim(long jobKey) {
    }

    @Override
    public void recordOutcome(long jobKey, JobOutcome outcome) {
        outcomes.put(jobKey, outcome);
    }
}
