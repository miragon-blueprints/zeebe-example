package io.miragon.blueprint.adapter.inbound.zeebe

import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class SendCancellationConfirmationWorkerTest {

    private val useCase = mockk<SendCancellationConfirmationUseCase>(relaxed = true)
    private val worker = SendCancellationConfirmationWorker(useCase)

    @Test
    fun `delegates to the use case for the application`() {
        // given
        val id = UUID.randomUUID()

        // when
        worker.handle(id.toString())

        // then
        verify { useCase.sendCancellationConfirmation(ApplicationId.of(id.toString())) }
    }
}
