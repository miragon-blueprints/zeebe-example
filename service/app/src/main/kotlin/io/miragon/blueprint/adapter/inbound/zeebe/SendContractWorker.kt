package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.SendContractUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

@Component
class SendContractWorker(
    private val useCase: SendContractUseCase,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_SEND_CONTRACT)
    fun handle(@Variable applicationId: String) {
        useCase.sendContract(ApplicationId.of(applicationId))
    }
}
