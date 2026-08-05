package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.ServiceTasks
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.domain.bike.OrderId
import org.springframework.stereotype.Component

@Component
class BookCostsWorker(
    private val useCase: BookCancellationCostsUseCase,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_BOOK_COSTS)
    fun handle(@Variable orderId: String) {
        useCase.bookCosts(OrderId(orderId))
    }
}
