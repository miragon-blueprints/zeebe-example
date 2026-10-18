package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.api.response.ActivatedJob
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class BookCostsWorkerTest {

    private val useCase = mockk<BookCancellationCostsUseCase>(relaxed = true)
    private val worker = BookCostsWorker(useCase, inMemoryIdempotentJobExecutor())
    private val job = mockk<ActivatedJob> { every { key } returns 1L }

    @Test
    fun `books the cancellation costs for the order`() {
        // when
        worker.handle(job, "ORDER-1")

        // then
        verify { useCase.bookCosts(OrderId("ORDER-1")) }
    }

    @Test
    fun `does not repeat the work on a repeated delivery of the same job`() {
        // when
        worker.handle(job, "ORDER-1")
        worker.handle(job, "ORDER-1")

        // then
        verify(exactly = 1) { useCase.bookCosts(OrderId("ORDER-1")) }
    }
}
