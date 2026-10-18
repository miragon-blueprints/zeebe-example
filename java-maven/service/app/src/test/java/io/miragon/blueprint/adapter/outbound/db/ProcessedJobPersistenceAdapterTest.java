package io.miragon.blueprint.adapter.outbound.db;

import static org.assertj.core.api.Assertions.assertThat;

import io.miragon.common.zeebe.idempotency.JobOutcome.BpmnErrorThrown;
import io.miragon.common.zeebe.idempotency.JobOutcome.Completed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(ProcessedJobPersistenceAdapter.class)
class ProcessedJobPersistenceAdapterTest {

    @Autowired
    private ProcessedJobPersistenceAdapter underTest;

    @Autowired
    private TestEntityManager entityManager;

    private final long jobKey = 2251799813685249L;

    @Test
    @DisplayName("an unknown job has no outcome")
    void anUnknownJobHasNoOutcome() {
        // given: an empty database
        // when / then: the lookup finds no outcome
        assertThat(underTest.findOutcome(jobKey)).isEmpty();
    }

    @Test
    @DisplayName("a claimed job has no outcome until it is recorded")
    void aClaimedJobHasNoOutcomeUntilItIsRecorded() {
        // given: a claimed job
        underTest.claim(jobKey);

        // when / then: the claim alone carries no outcome
        assertThat(underTest.findOutcome(jobKey)).isEmpty();
    }

    @Test
    @DisplayName("records the variables of a completed job")
    void recordsTheVariablesOfACompletedJob() {
        // given: a claimed job
        underTest.claim(jobKey);

        // when: its completion is recorded and re-read from a cleared persistence context
        underTest.recordOutcome(jobKey, new Completed("{\"orderId\":\"ORDER-1\"}"));
        entityManager.flush();
        entityManager.clear();

        // then: the recorded variables come back
        assertThat(underTest.findOutcome(jobKey)).contains(new Completed("{\"orderId\":\"ORDER-1\"}"));
    }

    @Test
    @DisplayName("records the BPMN error a job threw")
    void recordsTheBpmnErrorAJobThrew() {
        // given: a claimed job
        underTest.claim(jobKey);

        // when: the thrown BPMN error is recorded and re-read from a cleared persistence context
        underTest.recordOutcome(jobKey, new BpmnErrorThrown("bikeUnavailable", "Bike BIKE-OOS is not available"));
        entityManager.flush();
        entityManager.clear();

        // then: the recorded error comes back
        assertThat(underTest.findOutcome(jobKey))
            .contains(new BpmnErrorThrown("bikeUnavailable", "Bike BIKE-OOS is not available"));
    }
}
