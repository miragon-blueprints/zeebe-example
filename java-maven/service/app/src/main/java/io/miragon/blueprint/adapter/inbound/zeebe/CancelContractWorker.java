package io.miragon.blueprint.adapter.inbound.zeebe;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.springframework.stereotype.Component;

@Component
public class CancelContractWorker {

    private final CancelContractUseCase useCase;

    public CancelContractWorker(CancelContractUseCase useCase) {
        this.useCase = useCase;
    }

    @JobWorker(type = ServiceTasks.MIRAVELO_CANCEL_CONTRACT)
    public void handle(@Variable String applicationId) {
        useCase.cancelContract(ApplicationId.of(applicationId));
    }
}
