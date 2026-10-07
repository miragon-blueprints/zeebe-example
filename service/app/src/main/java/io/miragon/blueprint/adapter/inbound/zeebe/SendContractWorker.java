package io.miragon.blueprint.adapter.inbound.zeebe;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.SendContractUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.springframework.stereotype.Component;

@Component
public class SendContractWorker {

    private final SendContractUseCase useCase;

    public SendContractWorker(SendContractUseCase useCase) {
        this.useCase = useCase;
    }

    @JobWorker(type = ServiceTasks.MIRAVELO_SEND_CONTRACT)
    public void handle(@Variable String applicationId) {
        useCase.sendContract(ApplicationId.of(applicationId));
    }
}
