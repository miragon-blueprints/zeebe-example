package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.camunda.client.api.response.ActivatedJob
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.common.zeebe.idempotency.IdempotentJobExecutor
import org.springframework.stereotype.Component

@Component
class RequestCancellationWorker(
    private val useCase: RequestOrderCancellationUseCase,
    private val idempotentJobs: IdempotentJobExecutor,
) {

    // `orderId` is handed to the cancelBikeOrder sub-process by the calling activity.
    @JobWorker(type = ServiceTasks.MIRAVELO_REQUEST_CANCELLATION)
    fun handle(job: ActivatedJob, @Variable orderId: String): Map<String, Any?> =
        idempotentJobs.runOnceWithVariables(job) {
            val cancellationPossible = useCase.requestCancellation(OrderId(orderId))
            mapOf(FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.value to cancellationPossible)
        }
}
