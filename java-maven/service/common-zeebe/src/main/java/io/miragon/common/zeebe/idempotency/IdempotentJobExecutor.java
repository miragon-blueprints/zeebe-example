package io.miragon.common.zeebe.idempotency;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.BpmnError;
import io.camunda.client.exception.CamundaError;
import io.miragon.common.zeebe.idempotency.JobOutcome.BpmnErrorThrown;
import io.miragon.common.zeebe.idempotency.JobOutcome.Completed;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

public class IdempotentJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentJobExecutor.class);

    private final ProcessedJobStore store;

    private final TransactionOperations transaction;

    private final JsonMapper jsonMapper;

    public IdempotentJobExecutor(ProcessedJobStore store, TransactionOperations transaction, JsonMapper jsonMapper) {
        this.store = store;
        this.transaction = transaction;
        this.jsonMapper = jsonMapper;
    }

    public void runOnce(ActivatedJob job, Runnable work) {
        runOnceWithVariables(job, () -> {
            work.run();
            return Map.of();
        });
    }

    public Map<String, Object> runOnceWithVariables(ActivatedJob job, Supplier<Map<String, Object>> work) {
        JobOutcome outcome = transaction.execute(status -> recordedOutcome(job).orElseGet(() -> runAndRecord(job, work)));
        return switch (outcome) {
            case Completed completed -> jsonMapper.fromJsonAsMap(completed.variablesJson());
            case BpmnErrorThrown error -> throw CamundaError.bpmnError(error.errorCode(), error.errorMessage());
        };
    }

    private Optional<JobOutcome> recordedOutcome(ActivatedJob job) {
        Optional<JobOutcome> outcome = store.findOutcome(job.getKey());
        outcome.ifPresent(recorded -> log.info("Skipping already processed job {} ({})", job.getKey(), job.getType()));
        return outcome;
    }

    private JobOutcome runAndRecord(ActivatedJob job, Supplier<Map<String, Object>> work) {
        store.claim(job.getKey());
        JobOutcome outcome;
        try {
            outcome = new Completed(jsonMapper.toJson(work.get()));
        } catch (BpmnError error) {
            outcome = new BpmnErrorThrown(error.getErrorCode(), error.getErrorMessage());
        }
        store.recordOutcome(job.getKey(), outcome);
        return outcome;
    }
}
