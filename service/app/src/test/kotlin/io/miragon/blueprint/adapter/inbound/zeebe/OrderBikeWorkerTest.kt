package io.miragon.blueprint.adapter.inbound.zeebe

import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class OrderBikeWorkerTest {

    private val useCase = mockk<OrderBikeUseCase>()
    private val worker = OrderBikeWorker(useCase)

    @Test
    fun `returns the order id and availability as output variables`() {
        // given
        val id = UUID.randomUUID()
        every { useCase.orderBike(ApplicationId.of(id.toString())) } returns
            OrderBikeUseCase.Result(OrderId("ORDER-1"), bikeAvailable = true)

        // when
        val result = worker.handle(id.toString())

        // then
        assertThat(result)
            .containsEntry("orderId", "ORDER-1")
            .containsEntry("bikeAvailable", true)
    }

    @Test
    fun `reports an unavailable bike with a null order id`() {
        // given
        val id = UUID.randomUUID()
        every { useCase.orderBike(any()) } returns
            OrderBikeUseCase.Result(orderId = null, bikeAvailable = false)

        // when
        val result = worker.handle(id.toString())

        // then
        assertThat(result)
            .containsEntry("orderId", null)
            .containsEntry("bikeAvailable", false)
    }
}
