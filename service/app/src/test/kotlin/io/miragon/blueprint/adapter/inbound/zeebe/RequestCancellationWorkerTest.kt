package io.miragon.blueprint.adapter.inbound.zeebe

import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RequestCancellationWorkerTest {

    private val useCase = mockk<RequestOrderCancellationUseCase>()
    private val worker = RequestCancellationWorker(useCase)

    @Test
    fun `returns whether the cancellation is possible`() {
        // given
        every { useCase.requestCancellation(OrderId("ORDER-1")) } returns true

        // when
        val result = worker.handle("ORDER-1")

        // then
        assertThat(result).containsEntry("cancellationPossible", true)
    }
}
