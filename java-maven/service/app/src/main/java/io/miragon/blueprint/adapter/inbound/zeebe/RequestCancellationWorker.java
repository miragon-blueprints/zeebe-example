package io.miragon.blueprint.adapter.inbound.zeebe;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.common.zeebe.idempotency.IdempotentJobExecutor;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RequestCancellationWorker {

    private final RequestOrderCancellationUseCase useCase;

    private final IdempotentJobExecutor idempotentJobs;

    public RequestCancellationWorker(RequestOrderCancellationUseCase useCase, IdempotentJobExecutor idempotentJobs) {
        this.useCase = useCase;
        this.idempotentJobs = idempotentJobs;
    }

    // `orderId` is handed to the cancelBikeOrder sub-process by the calling activity.
    @JobWorker(type = ServiceTasks.MIRAVELO_REQUEST_CANCELLATION)
    public Map<String, Object> handle(ActivatedJob job, @Variable String orderId) {
        return idempotentJobs.runOnceWithVariables(job, () -> {
            boolean cancellationPossible = useCase.requestCancellation(new OrderId(orderId));
            return Map.of(FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.getValue(), cancellationPossible);
        });
    }
}
