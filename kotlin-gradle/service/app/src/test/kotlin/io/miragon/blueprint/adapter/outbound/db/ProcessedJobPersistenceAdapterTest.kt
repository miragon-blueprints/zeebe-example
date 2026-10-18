package io.miragon.blueprint.adapter.outbound.db

import io.miragon.common.zeebe.idempotency.JobOutcome.BpmnErrorThrown
import io.miragon.common.zeebe.idempotency.JobOutcome.Completed
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
@Import(ProcessedJobPersistenceAdapter::class)
class ProcessedJobPersistenceAdapterTest {

    @Autowired
    private lateinit var underTest: ProcessedJobPersistenceAdapter

    @Autowired
    private lateinit var entityManager: TestEntityManager

    private val jobKey = 2251799813685249L

    @Test
    fun `an unknown job has no outcome`() {

        // given: an empty database
        // when / then: the lookup finds no outcome
        assertThat(underTest.findOutcome(jobKey)).isNull()
    }

    @Test
    fun `a claimed job has no outcome until it is recorded`() {

        // given: a claimed job
        underTest.claim(jobKey)

        // when / then: the claim alone carries no outcome
        assertThat(underTest.findOutcome(jobKey)).isNull()
    }

    @Test
    fun `records the variables of a completed job`() {

        // given: a claimed job
        underTest.claim(jobKey)

        // when: its completion is recorded and re-read from a cleared persistence context
        underTest.recordOutcome(jobKey, Completed("""{"orderId":"ORDER-1"}"""))
        entityManager.flush()
        entityManager.clear()

        // then: the recorded variables come back
        assertThat(underTest.findOutcome(jobKey)).isEqualTo(Completed("""{"orderId":"ORDER-1"}"""))
    }

    @Test
    fun `records the BPMN error a job threw`() {

        // given: a claimed job
        underTest.claim(jobKey)

        // when: the thrown BPMN error is recorded and re-read from a cleared persistence context
        underTest.recordOutcome(jobKey, BpmnErrorThrown("bikeUnavailable", "Bike BIKE-OOS is not available"))
        entityManager.flush()
        entityManager.clear()

        // then: the recorded error comes back
        assertThat(underTest.findOutcome(jobKey))
            .isEqualTo(BpmnErrorThrown("bikeUnavailable", "Bike BIKE-OOS is not available"))
    }
}
