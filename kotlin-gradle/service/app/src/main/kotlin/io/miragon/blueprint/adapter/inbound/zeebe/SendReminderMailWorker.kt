package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.camunda.client.api.response.ActivatedJob
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.common.zeebe.idempotency.IdempotentJobExecutor
import org.springframework.stereotype.Component

@Component
class SendReminderMailWorker(
    private val useCase: SendSignatureReminderUseCase,
    private val idempotentJobs: IdempotentJobExecutor,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_SEND_REMINDER_MAIL)
    fun handle(job: ActivatedJob, @Variable applicationId: String) {
        idempotentJobs.runOnce(job) { useCase.sendSignatureReminder(ApplicationId.of(applicationId)) }
    }
}
