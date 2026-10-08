package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.api.worker.JobClient
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException
import org.springframework.stereotype.Component

/**
 * Validates the application. An invalid application is signalled to the process as the BPMN error
 * `applicationInvalid`, which the error boundary event on the service task catches. `autoComplete` is
 * off so we decide explicitly between completing the job and throwing the error.
 */
@Component
class ValidateApplicationWorker(
    private val useCase: ValidateApplicationUseCase,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_VALIDATE_APPLICATION, autoComplete = [false])
    fun handle(client: JobClient, job: ActivatedJob, @Variable applicationId: String) {
        try {
            useCase.validate(ApplicationId.of(applicationId))
            client.newCompleteCommand(job).send().join()
        } catch (e: ApplicationInvalidException) {
            client.newThrowErrorCommand(job)
                .errorCode("applicationInvalid")
                .errorMessage(e.reason)
                .send()
                .join()
        }
    }
}
