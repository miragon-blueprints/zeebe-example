package io.miragon.blueprint.adapter.inbound.zeebe

import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class SendReminderMailWorkerTest {

    private val useCase = mockk<SendSignatureReminderUseCase>(relaxed = true)
    private val worker = SendReminderMailWorker(useCase)

    @Test
    fun `delegates to the use case for the application`() {
        // given
        val id = UUID.randomUUID()

        // when
        worker.handle(id.toString())

        // then
        verify { useCase.sendSignatureReminder(ApplicationId.of(id.toString())) }
    }
}
