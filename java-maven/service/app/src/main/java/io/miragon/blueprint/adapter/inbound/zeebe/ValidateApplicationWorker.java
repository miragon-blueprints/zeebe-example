package io.miragon.blueprint.adapter.inbound.zeebe;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException;
import org.springframework.stereotype.Component;

/**
 * Validates the application. An invalid application is signalled to the process as the BPMN error
 * {@code applicationInvalid}, which the error boundary event on the service task catches. {@code autoComplete} is
 * off so we decide explicitly between completing the job and throwing the error.
 */
@Component
public class ValidateApplicationWorker {

    private final ValidateApplicationUseCase useCase;

    public ValidateApplicationWorker(ValidateApplicationUseCase useCase) {
        this.useCase = useCase;
    }

    @JobWorker(type = ServiceTasks.MIRAVELO_VALIDATE_APPLICATION, autoComplete = false)
    public void handle(JobClient client, ActivatedJob job, @Variable String applicationId) {
        try {
            useCase.validate(ApplicationId.of(applicationId));
            client.newCompleteCommand(job).send().join();
        } catch (ApplicationInvalidException e) {
            client.newThrowErrorCommand(job)
                .errorCode("applicationInvalid")
                .errorMessage(e.getReason())
                .send()
                .join();
        }
    }
}
