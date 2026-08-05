package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.ServiceTasks
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

@Component
class SendRejectionWorker(
    private val useCase: RejectApplicationUseCase,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_SEND_REJECTION)
    fun handle(@Variable applicationId: String) {
        useCase.reject(ApplicationId.of(applicationId))
    }
}
