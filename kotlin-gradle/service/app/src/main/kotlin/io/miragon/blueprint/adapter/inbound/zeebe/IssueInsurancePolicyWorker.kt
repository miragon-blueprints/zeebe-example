package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.camunda.client.api.response.ActivatedJob
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.common.zeebe.idempotency.IdempotentJobExecutor
import org.springframework.stereotype.Component

@Component
class IssueInsurancePolicyWorker(
    private val useCase: IssueInsurancePolicyUseCase,
    private val idempotentJobs: IdempotentJobExecutor,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_ISSUE_INSURANCE_POLICY)
    fun handle(job: ActivatedJob, @Variable applicationId: String) {
        idempotentJobs.runOnce(job) { useCase.issuePolicy(ApplicationId.of(applicationId)) }
    }
}
