package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.api.response.ActivatedJob
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RequestCancellationWorkerTest {

    private val useCase = mockk<RequestOrderCancellationUseCase>()
    private val worker = RequestCancellationWorker(useCase, inMemoryIdempotentJobExecutor())
    private val job = mockk<ActivatedJob> { every { key } returns 1L }

    @Test
    fun `returns whether the cancellation is possible`() {
        // given
        every { useCase.requestCancellation(OrderId("ORDER-1")) } returns true

        // when
        val result = worker.handle(job, "ORDER-1")

        // then
        assertThat(result).containsEntry("cancellationPossible", true)
    }

    @Test
    fun `answers a repeated delivery of the same job with the first answer without asking the dealer again`() {
        // given
        every { useCase.requestCancellation(OrderId("ORDER-1")) } returnsMany listOf(true, false)
        worker.handle(job, "ORDER-1")

        // when
        val result = worker.handle(job, "ORDER-1")

        // then
        assertThat(result).containsEntry("cancellationPossible", true)
        verify(exactly = 1) { useCase.requestCancellation(OrderId("ORDER-1")) }
    }
}
