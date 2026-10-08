package io.miragon.blueprint.adapter.inbound.zeebe;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.springframework.stereotype.Component;

/**
 * Activates the leasing once the withdrawal period has elapsed: {@code serviceTask_activateLeasing} runs
 * just before {@code endEvent_leasingActive} and flips the read model to ACTIVE. This is the Zeebe
 * counterpart of the embedded engine's activate-leasing delegate on the {@code miravelo.leasingActivated}
 * message end event — Zeebe has no delegate-on-end-event, so the side effect lives in a job worker on
 * a dedicated service task instead.
 */
@Component
public class ActivateLeasingWorker {

    private final ActivateLeasingUseCase useCase;

    public ActivateLeasingWorker(ActivateLeasingUseCase useCase) {
        this.useCase = useCase;
    }

    @JobWorker(type = ServiceTasks.MIRAVELO_ACTIVATE_LEASING)
    public void handle(@Variable String applicationId) {
        useCase.activate(ApplicationId.of(applicationId));
    }
}
