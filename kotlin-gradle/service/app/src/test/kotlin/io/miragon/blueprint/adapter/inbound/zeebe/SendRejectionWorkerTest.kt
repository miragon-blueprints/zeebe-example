package io.miragon.blueprint.adapter.inbound.zeebe

import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class SendRejectionWorkerTest {

    private val useCase = mockk<RejectApplicationUseCase>(relaxed = true)
    private val worker = SendRejectionWorker(useCase)

    @Test
    fun `delegates to the use case for the application`() {
        // given
        val id = UUID.randomUUID()

        // when
        worker.handle(id.toString())

        // then
        verify { useCase.reject(ApplicationId.of(id.toString())) }
    }
}
