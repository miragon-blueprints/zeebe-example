package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.api.worker.JobClient
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class ValidateApplicationWorkerTest {

    private val useCase = mockk<ValidateApplicationUseCase>()
    private val worker = ValidateApplicationWorker(useCase)
    private val client = mockk<JobClient>(relaxed = true)
    private val job = mockk<ActivatedJob>(relaxed = true)

    @Test
    fun `completes the job for a valid application`() {
        // given
        val id = UUID.randomUUID()
        every { useCase.validate(ApplicationId.of(id.toString())) } just Runs

        // when
        worker.handle(client, job, id.toString())

        // then
        verify { client.newCompleteCommand(job) }
        verify(exactly = 0) { client.newThrowErrorCommand(any<ActivatedJob>()) }
    }

    @Test
    fun `throws the applicationInvalid BPMN error for an invalid application`() {
        // given
        val id = UUID.randomUUID()
        every { useCase.validate(any()) } throws
            ApplicationInvalidException(ApplicationId.of(id.toString()), "too young")

        // when
        worker.handle(client, job, id.toString())

        // then
        verify { client.newThrowErrorCommand(job) }
        verify(exactly = 0) { client.newCompleteCommand(any<ActivatedJob>()) }
    }
}
