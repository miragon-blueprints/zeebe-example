package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.impl.CamundaObjectMapper
import io.miragon.common.zeebe.idempotency.IdempotentJobExecutor
import io.miragon.common.zeebe.idempotency.JobOutcome
import io.miragon.common.zeebe.idempotency.ProcessedJobStore
import org.springframework.transaction.support.TransactionOperations

class InMemoryProcessedJobStore : ProcessedJobStore {

    private val outcomes = mutableMapOf<Long, JobOutcome>()

    override fun findOutcome(jobKey: Long): JobOutcome? = outcomes[jobKey]

    override fun claim(jobKey: Long) = Unit

    override fun recordOutcome(jobKey: Long, outcome: JobOutcome) {
        outcomes[jobKey] = outcome
    }
}

fun inMemoryIdempotentJobExecutor() =
    IdempotentJobExecutor(InMemoryProcessedJobStore(), TransactionOperations.withoutTransaction(), CamundaObjectMapper())
