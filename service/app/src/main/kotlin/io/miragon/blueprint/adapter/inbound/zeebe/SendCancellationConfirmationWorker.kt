package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.ServiceTasks
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

@Component
class SendCancellationConfirmationWorker(
    private val useCase: SendCancellationConfirmationUseCase,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_SEND_CANCELLATION_CONFIRMATION)
    fun handle(@Variable applicationId: String) {
        useCase.sendCancellationConfirmation(ApplicationId.of(applicationId))
    }
}
