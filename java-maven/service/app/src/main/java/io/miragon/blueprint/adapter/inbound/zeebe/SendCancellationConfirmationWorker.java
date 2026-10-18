package io.miragon.blueprint.adapter.inbound.zeebe;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.common.zeebe.idempotency.IdempotentJobExecutor;
import org.springframework.stereotype.Component;

@Component
public class SendCancellationConfirmationWorker {

    private final SendCancellationConfirmationUseCase useCase;

    private final IdempotentJobExecutor idempotentJobs;

    public SendCancellationConfirmationWorker(SendCancellationConfirmationUseCase useCase, IdempotentJobExecutor idempotentJobs) {
        this.useCase = useCase;
        this.idempotentJobs = idempotentJobs;
    }

    @JobWorker(type = ServiceTasks.MIRAVELO_SEND_CANCELLATION_CONFIRMATION)
    public void handle(ActivatedJob job, @Variable String applicationId) {
        idempotentJobs.runOnce(job, () -> useCase.sendCancellationConfirmation(ApplicationId.of(applicationId)));
    }
}
