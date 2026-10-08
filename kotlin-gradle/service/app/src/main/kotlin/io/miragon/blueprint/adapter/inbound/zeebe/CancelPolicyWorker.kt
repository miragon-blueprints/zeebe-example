package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

@Component
class CancelPolicyWorker(
    private val useCase: CancelInsurancePolicyUseCase,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_CANCEL_POLICY)
    fun handle(@Variable applicationId: String) {
        useCase.cancelPolicy(ApplicationId.of(applicationId))
    }
}
