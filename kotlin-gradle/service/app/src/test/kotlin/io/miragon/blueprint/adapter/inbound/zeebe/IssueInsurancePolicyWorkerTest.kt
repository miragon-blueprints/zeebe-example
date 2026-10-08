package io.miragon.blueprint.adapter.inbound.zeebe

import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class IssueInsurancePolicyWorkerTest {

    private val useCase = mockk<IssueInsurancePolicyUseCase>(relaxed = true)
    private val worker = IssueInsurancePolicyWorker(useCase)

    @Test
    fun `delegates to the use case for the application`() {
        // given
        val id = UUID.randomUUID()

        // when
        worker.handle(id.toString())

        // then
        verify { useCase.issuePolicy(ApplicationId.of(id.toString())) }
    }
}
