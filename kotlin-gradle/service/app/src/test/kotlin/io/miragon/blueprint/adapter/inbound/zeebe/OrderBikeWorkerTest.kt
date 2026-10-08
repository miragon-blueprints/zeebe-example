package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.api.worker.JobClient
import io.miragon.blueprint.adapter.outbound.dealer.BikeDealerAdapter.DealerUnavailableException
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class OrderBikeWorkerTest {

    private val useCase = mockk<OrderBikeUseCase>()
    private val worker = OrderBikeWorker(useCase)
    private val client = mockk<JobClient>(relaxed = true)
    private val job = mockk<ActivatedJob>(relaxed = true)

    @Test
    fun `completes the job with the order id and availability as output variables`() {
        // given
        val id = UUID.randomUUID()
        every { useCase.orderBike(ApplicationId.of(id.toString())) } returns
            OrderBikeUseCase.Result(OrderId("ORDER-1"), bikeAvailable = true)

        // when
        worker.handle(client, job, id.toString())

        // then
        val variables = slot<Map<String, Any?>>()
        verify { client.newCompleteCommand(job).variables(capture(variables)) }
        verify(exactly = 0) { client.newFailCommand(any<ActivatedJob>()) }
        assertThat(variables.captured)
            .containsEntry("orderId", "ORDER-1")
            .containsEntry("bikeAvailable", true)
    }

    @Test
    fun `completes the job reporting an unavailable bike with a null order id`() {
        // given
        val id = UUID.randomUUID()
        every { useCase.orderBike(any()) } returns
            OrderBikeUseCase.Result(orderId = null, bikeAvailable = false)

        // when
        worker.handle(client, job, id.toString())

        // then
        val variables = slot<Map<String, Any?>>()
        verify { client.newCompleteCommand(job).variables(capture(variables)) }
        verify(exactly = 0) { client.newFailCommand(any<ActivatedJob>()) }
        assertThat(variables.captured)
            .containsEntry("orderId", null)
            .containsEntry("bikeAvailable", false)
    }

    @Test
    fun `fails the job with a backoff when the dealer outage throws so retries count down to an incident`() {
        // given: the simulated dealer outage (BIKE-FAIL) propagates out of the use case
        val id = UUID.randomUUID()
        every { useCase.orderBike(any()) } throws DealerUnavailableException(BikeId("BIKE-FAIL"))
        every { job.retries } returns 3

        // when
        worker.handle(client, job, id.toString())

        // then: the job is failed (not completed) with one fewer retry, so Zeebe eventually raises an incident
        verify { client.newFailCommand(job).retries(2) }
        verify(exactly = 0) { client.newCompleteCommand(any<ActivatedJob>()) }
    }
}
