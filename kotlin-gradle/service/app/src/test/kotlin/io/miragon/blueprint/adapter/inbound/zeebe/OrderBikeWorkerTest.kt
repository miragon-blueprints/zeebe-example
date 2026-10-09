package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.api.worker.JobClient
import io.miragon.blueprint.adapter.outbound.dealer.BikeDealerAdapter.DealerUnavailableException
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.BikeUnavailableException
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
    fun `completes the job with the order id as output variable`() {
        // given
        val id = UUID.randomUUID()
        every { useCase.orderBike(ApplicationId.of(id.toString()), BikeId("BIKE-900")) } returns OrderId("ORDER-1")

        // when
        worker.handle(client, job, id.toString(), "BIKE-900")

        // then
        val variables = slot<Map<String, Any?>>()
        verify { client.newCompleteCommand(job).variables(capture(variables)) }
        verify(exactly = 0) { client.newThrowErrorCommand(any<ActivatedJob>()) }
        verify(exactly = 0) { client.newFailCommand(any<ActivatedJob>()) }
        assertThat(variables.captured).isEqualTo(mapOf("orderId" to "ORDER-1"))
    }

    @Test
    fun `throws the bikeUnavailable BPMN error when the dealer cannot deliver the bike`() {
        // given
        val id = UUID.randomUUID()
        every { useCase.orderBike(any(), any()) } throws BikeUnavailableException(BikeId("BIKE-OOS"))

        // when
        worker.handle(client, job, id.toString(), "BIKE-OOS")

        // then: the BPMN error is thrown and the job is neither completed nor failed
        verify {
            client.newThrowErrorCommand(job)
                .errorCode("bikeUnavailable")
                .errorMessage("Bike BIKE-OOS is not available at the dealer")
        }
        verify(exactly = 0) { client.newCompleteCommand(any<ActivatedJob>()) }
        verify(exactly = 0) { client.newFailCommand(any<ActivatedJob>()) }
    }

    @Test
    fun `fails the job with a backoff when the dealer outage throws so retries count down to an incident`() {
        // given: the simulated dealer outage (BIKE-FAIL) propagates out of the use case
        val id = UUID.randomUUID()
        every { useCase.orderBike(any(), any()) } throws DealerUnavailableException(BikeId("BIKE-FAIL"))
        every { job.retries } returns 3

        // when
        worker.handle(client, job, id.toString(), "BIKE-FAIL")

        // then: the job is failed (not completed) with one fewer retry, so Zeebe eventually raises an incident
        verify { client.newFailCommand(job).retries(2) }
        verify(exactly = 0) { client.newCompleteCommand(any<ActivatedJob>()) }
        verify(exactly = 0) { client.newThrowErrorCommand(any<ActivatedJob>()) }
    }
}
