package io.miragon.blueprint.adapter.outbound.db

import io.miragon.common.zeebe.idempotency.JobOutcome
import io.miragon.common.zeebe.idempotency.JobOutcome.BpmnErrorThrown
import io.miragon.common.zeebe.idempotency.JobOutcome.Completed
import io.miragon.common.zeebe.idempotency.ProcessedJobStore
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime

@Component
class ProcessedJobPersistenceAdapter(
    private val repository: ProcessedJobJpaRepository,
    private val clock: Clock,
) : ProcessedJobStore {

    override fun findOutcome(jobKey: Long): JobOutcome? =
        repository.findById(jobKey).orElse(null)?.toOutcome()

    override fun claim(jobKey: Long) {
        repository.saveAndFlush(ProcessedJobEntity(jobKey = jobKey, processedAt = LocalDateTime.now(clock)))
    }

    override fun recordOutcome(jobKey: Long, outcome: JobOutcome) {
        val processedAt = LocalDateTime.now(clock)
        val entity =
            when (outcome) {
                is Completed -> ProcessedJobEntity(jobKey, processedAt, variables = outcome.variablesJson)
                is BpmnErrorThrown ->
                    ProcessedJobEntity(jobKey, processedAt, errorCode = outcome.errorCode, errorMessage = outcome.errorMessage)
            }
        repository.save(entity)
    }

    private fun ProcessedJobEntity.toOutcome(): JobOutcome? =
        variables?.let(::Completed) ?: errorCode?.let { BpmnErrorThrown(it, errorMessage) }
}
