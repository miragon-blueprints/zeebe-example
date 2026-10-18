package io.miragon.common.zeebe.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.BpmnError;
import io.camunda.client.exception.CamundaError;
import io.camunda.client.impl.CamundaObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

class IdempotentJobExecutorTest {

    private final RecordingProcessedJobStore store = new RecordingProcessedJobStore();

    private final IdempotentJobExecutor underTest =
        new IdempotentJobExecutor(store, TransactionOperations.withoutTransaction(), new CamundaObjectMapper());

    private final ActivatedJob job = mock(ActivatedJob.class);

    IdempotentJobExecutorTest() {
        when(job.getKey()).thenReturn(4711L);
    }

    @Test
    @DisplayName("runs the work on the first delivery and returns its variables")
    void runsTheWorkOnTheFirstDeliveryAndReturnsItsVariables() {

        Map<String, Object> variables = underTest.runOnceWithVariables(job, () -> Map.of("orderId", "ORDER-1"));

        assertThat(variables).isEqualTo(Map.of("orderId", "ORDER-1"));
        assertThat(store.events).containsExactly("claim 4711", "record 4711");
    }

    @Test
    @DisplayName("skips the work on a repeated delivery and returns the recorded variables")
    void skipsTheWorkOnARepeatedDeliveryAndReturnsTheRecordedVariables() {
        underTest.runOnceWithVariables(job, () -> Map.of("orderId", "ORDER-1"));
        AtomicInteger runs = new AtomicInteger();

        Map<String, Object> variables = underTest.runOnceWithVariables(job, () -> {
            runs.incrementAndGet();
            return Map.of("orderId", "ORDER-2");
        });

        assertThat(runs).hasValue(0);
        assertThat(variables).isEqualTo(Map.of("orderId", "ORDER-1"));
    }

    @Test
    @DisplayName("claims the job before the work runs")
    void claimsTheJobBeforeTheWorkRuns() {

        underTest.runOnce(job, () -> store.events.add("work"));

        assertThat(store.events).containsExactly("claim 4711", "work", "record 4711");
    }

    @Test
    @DisplayName("records a BPMN error and throws it again on a repeated delivery without running the work")
    void recordsABpmnErrorAndThrowsItAgainOnARepeatedDeliveryWithoutRunningTheWork() {
        assertThatThrownBy(() -> underTest.runOnce(job, () -> {
            throw CamundaError.bpmnError("bikeUnavailable", "out of stock");
        }))
            .isInstanceOf(BpmnError.class);
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> underTest.runOnce(job, runs::incrementAndGet))
            .isInstanceOfSatisfying(BpmnError.class, error -> {
                assertThat(error.getErrorCode()).isEqualTo("bikeUnavailable");
                assertThat(error.getErrorMessage()).isEqualTo("out of stock");
            });
        assertThat(runs).hasValue(0);
    }

    @Test
    @DisplayName("records no outcome when the work fails so the rolled back claim lets a retry run it again")
    void recordsNoOutcomeWhenTheWorkFailsSoTheRolledBackClaimLetsARetryRunItAgain() {

        assertThatThrownBy(() -> underTest.runOnce(job, () -> {
            throw new IllegalStateException("dealer down");
        }))
            .isInstanceOf(IllegalStateException.class);

        assertThat(store.events).containsExactly("claim 4711");
    }

    private static class RecordingProcessedJobStore implements ProcessedJobStore {

        private final List<String> events = new ArrayList<>();

        private final Map<Long, JobOutcome> outcomes = new HashMap<>();

        @Override
        public Optional<JobOutcome> findOutcome(long jobKey) {
            return Optional.ofNullable(outcomes.get(jobKey));
        }

        @Override
        public void claim(long jobKey) {
            events.add("claim " + jobKey);
            outcomes.put(jobKey, null);
        }

        @Override
        public void recordOutcome(long jobKey, JobOutcome outcome) {
            events.add("record " + jobKey);
            outcomes.put(jobKey, outcome);
        }
    }
}
