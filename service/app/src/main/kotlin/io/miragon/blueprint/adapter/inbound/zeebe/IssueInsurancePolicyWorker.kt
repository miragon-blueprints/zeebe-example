package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.ServiceTasks
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

@Component
class IssueInsurancePolicyWorker(
    private val useCase: IssueInsurancePolicyUseCase,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_ISSUE_INSURANCE_POLICY)
    fun handle(@Variable applicationId: String) {
        useCase.issuePolicy(ApplicationId.of(applicationId))
    }
}
