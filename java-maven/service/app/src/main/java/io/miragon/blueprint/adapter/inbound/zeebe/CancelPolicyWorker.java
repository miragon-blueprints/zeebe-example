package io.miragon.blueprint.adapter.inbound.zeebe;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.springframework.stereotype.Component;

@Component
public class CancelPolicyWorker {

    private final CancelInsurancePolicyUseCase useCase;

    public CancelPolicyWorker(CancelInsurancePolicyUseCase useCase) {
        this.useCase = useCase;
    }

    @JobWorker(type = ServiceTasks.MIRAVELO_CANCEL_POLICY)
    public void handle(@Variable String applicationId) {
        useCase.cancelPolicy(ApplicationId.of(applicationId));
    }
}
