package io.miragon.blueprint.adapter.inbound.zeebe;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.springframework.stereotype.Component;

@Component
public class SendRejectionWorker {

    private final RejectApplicationUseCase useCase;

    public SendRejectionWorker(RejectApplicationUseCase useCase) {
        this.useCase = useCase;
    }

    @JobWorker(type = ServiceTasks.MIRAVELO_SEND_REJECTION)
    public void handle(@Variable String applicationId) {
        useCase.reject(ApplicationId.of(applicationId));
    }
}
