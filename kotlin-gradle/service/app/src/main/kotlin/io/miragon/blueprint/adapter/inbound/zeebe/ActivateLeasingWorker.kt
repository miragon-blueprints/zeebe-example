package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

/**
 * Activates the leasing once the withdrawal period has elapsed: `serviceTask_activateLeasing` runs
 * just before `endEvent_leasingActive` and flips the read model to ACTIVE. This is the Zeebe
 * counterpart of the embedded engine's activate-leasing delegate on the `miravelo.leasingActivated`
 * message end event — Zeebe has no delegate-on-end-event, so the side effect lives in a job worker on
 * a dedicated service task instead.
 */
@Component
class ActivateLeasingWorker(
    private val useCase: ActivateLeasingUseCase,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_ACTIVATE_LEASING)
    fun handle(@Variable applicationId: String) {
        useCase.activate(ApplicationId.of(applicationId))
    }
}
