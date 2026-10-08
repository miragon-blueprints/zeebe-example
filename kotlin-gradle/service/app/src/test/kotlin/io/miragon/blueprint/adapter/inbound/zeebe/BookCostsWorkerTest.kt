package io.miragon.blueprint.adapter.inbound.zeebe

import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class BookCostsWorkerTest {

    private val useCase = mockk<BookCancellationCostsUseCase>(relaxed = true)
    private val worker = BookCostsWorker(useCase)

    @Test
    fun `books the cancellation costs for the order`() {
        // when
        worker.handle("ORDER-1")

        // then
        verify { useCase.bookCosts(OrderId("ORDER-1")) }
    }
}
