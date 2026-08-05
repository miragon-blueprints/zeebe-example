package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.ServiceTasks
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

@Component
class CancelContractWorker(
    private val useCase: CancelContractUseCase,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_CANCEL_CONTRACT)
    fun handle(@Variable applicationId: String) {
        useCase.cancelContract(ApplicationId.of(applicationId))
    }
}
