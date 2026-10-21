package io.miragon.common.zeebe.idempotency

import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.exception.BpmnError
import io.camunda.client.exception.CamundaError
import io.camunda.client.impl.CamundaObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionOperations

class IdempotentJobExecutorTest {

    private val store = RecordingProcessedJobStore()
    private val underTest = IdempotentJobExecutor(store, TransactionOperations.withoutTransaction(), CamundaObjectMapper())
    private val job = mockk<ActivatedJob> { every { key } returns 4711L }

    @Test
    fun `runs the work on the first delivery and returns its variables`() {

        val variables = underTest.runOnceWithVariables(job) { mapOf("orderId" to "ORDER-1") }

        assertThat(variables).isEqualTo(mapOf("orderId" to "ORDER-1"))
        assertThat(store.events).containsExactly("claim 4711", "record 4711")
    }

    @Test
    fun `skips the work on a repeated delivery and returns the recorded variables`() {
        underTest.runOnceWithVariables(job) { mapOf("orderId" to "ORDER-1") }
        var runs = 0

        val variables = underTest.runOnceWithVariables(job) {
            runs++
            mapOf("orderId" to "ORDER-2")
        }

        assertThat(runs).isZero()
        assertThat(variables).isEqualTo(mapOf("orderId" to "ORDER-1"))
    }

    @Test
    fun `claims the job before the work runs`() {

        underTest.runOnce(job) { store.events += "work" }

        assertThat(store.events).containsExactly("claim 4711", "work", "record 4711")
    }

    @Test
    fun `records a BPMN error and throws it again on a repeated delivery without running the work`() {
        assertThatThrownBy { underTest.runOnce(job) { throw CamundaError.bpmnError("bikeUnavailable", "out of stock") } }
            .isInstanceOf(BpmnError::class.java)
        var runs = 0

        assertThatThrownBy { underTest.runOnce(job) { runs++ } }
            .isInstanceOfSatisfying(BpmnError::class.java) {
                assertThat(it.errorCode).isEqualTo("bikeUnavailable")
                assertThat(it.errorMessage).isEqualTo("out of stock")
            }
        assertThat(runs).isZero()
    }

    @Test
    fun `records no outcome when the work fails so the rolled back claim lets a retry run it again`() {

        assertThatThrownBy { underTest.runOnce(job) { error("dealer down") } }
            .isInstanceOf(IllegalStateException::class.java)

        assertThat(store.events).containsExactly("claim 4711")
    }

    private class RecordingProcessedJobStore : ProcessedJobStore {

        val events = mutableListOf<String>()
        private val outcomes = mutableMapOf<Long, JobOutcome?>()

        override fun findOutcome(jobKey: Long): JobOutcome? = outcomes[jobKey]

        override fun claim(jobKey: Long) {
            events += "claim $jobKey"
            outcomes[jobKey] = null
        }

        override fun recordOutcome(jobKey: Long, outcome: JobOutcome) {
            events += "record $jobKey"
            outcomes[jobKey] = outcome
        }
    }
}
