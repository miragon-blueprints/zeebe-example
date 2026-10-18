package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.camunda.client.api.response.ActivatedJob
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.common.zeebe.idempotency.IdempotentJobExecutor
import org.springframework.stereotype.Component

@Component
class BookCostsWorker(
    private val useCase: BookCancellationCostsUseCase,
    private val idempotentJobs: IdempotentJobExecutor,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_BOOK_COSTS)
    fun handle(job: ActivatedJob, @Variable orderId: String) {
        idempotentJobs.runOnce(job) { useCase.bookCosts(OrderId(orderId)) }
    }
}
