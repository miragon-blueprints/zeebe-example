package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.camunda.client.api.response.ActivatedJob
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.common.zeebe.idempotency.IdempotentJobExecutor
import org.springframework.stereotype.Component

@Component
class SendCancellationConfirmationWorker(
    private val useCase: SendCancellationConfirmationUseCase,
    private val idempotentJobs: IdempotentJobExecutor,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_SEND_CANCELLATION_CONFIRMATION)
    fun handle(job: ActivatedJob, @Variable applicationId: String) {
        idempotentJobs.runOnce(job) { useCase.sendCancellationConfirmation(ApplicationId.of(applicationId)) }
    }
}
