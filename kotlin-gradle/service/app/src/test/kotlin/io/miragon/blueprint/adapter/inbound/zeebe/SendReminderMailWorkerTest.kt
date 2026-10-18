package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.api.response.ActivatedJob
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class SendReminderMailWorkerTest {

    private val useCase = mockk<SendSignatureReminderUseCase>(relaxed = true)
    private val worker = SendReminderMailWorker(useCase, inMemoryIdempotentJobExecutor())
    private val job = mockk<ActivatedJob> { every { key } returns 1L }

    @Test
    fun `delegates to the use case for the application`() {
        // given
        val id = UUID.randomUUID()

        // when
        worker.handle(job, id.toString())

        // then
        verify { useCase.sendSignatureReminder(ApplicationId.of(id.toString())) }
    }

    @Test
    fun `does not repeat the work on a repeated delivery of the same job`() {
        // given
        val id = UUID.randomUUID()

        // when
        worker.handle(job, id.toString())
        worker.handle(job, id.toString())

        // then
        verify(exactly = 1) { useCase.sendSignatureReminder(ApplicationId.of(id.toString())) }
    }
}
