package io.miragon.common.zeebe.idempotency

import io.camunda.client.api.JsonMapper
import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.exception.BpmnError
import io.camunda.client.exception.CamundaError
import io.miragon.common.zeebe.idempotency.JobOutcome.BpmnErrorThrown
import io.miragon.common.zeebe.idempotency.JobOutcome.Completed
import mu.KotlinLogging
import org.springframework.transaction.support.TransactionOperations

class IdempotentJobExecutor(
    private val store: ProcessedJobStore,
    private val transaction: TransactionOperations,
    private val jsonMapper: JsonMapper,
) {

    private val log = KotlinLogging.logger {}

    fun runOnce(job: ActivatedJob, work: () -> Unit) {
        runOnceWithVariables(job) {
            work()
            emptyMap()
        }
    }

    fun runOnceWithVariables(job: ActivatedJob, work: () -> Map<String, Any?>): Map<String, Any?> {
        val outcome = transaction.execute { recordedOutcome(job) ?: runAndRecord(job, work) }!!
        return when (outcome) {
            is Completed -> jsonMapper.fromJsonAsMap(outcome.variablesJson)
            is BpmnErrorThrown -> throw CamundaError.bpmnError(outcome.errorCode, outcome.errorMessage)
        }
    }

    private fun recordedOutcome(job: ActivatedJob): JobOutcome? =
        store.findOutcome(job.key)?.also { log.info { "Skipping already processed job ${job.key} (${job.type})" } }

    private fun runAndRecord(job: ActivatedJob, work: () -> Map<String, Any?>): JobOutcome {
        store.claim(job.key)
        val outcome =
            try {
                Completed(jsonMapper.toJson(work()))
            } catch (error: BpmnError) {
                BpmnErrorThrown(error.errorCode, error.errorMessage)
            }
        store.recordOutcome(job.key, outcome)
        return outcome
    }
}
