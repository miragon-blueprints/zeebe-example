package io.miragon.blueprint.adapter.inbound.zeebe;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RequestCancellationWorker {

    private final RequestOrderCancellationUseCase useCase;

    public RequestCancellationWorker(RequestOrderCancellationUseCase useCase) {
        this.useCase = useCase;
    }

    // `orderId` is handed to the cancelBikeOrder sub-process by the calling activity.
    @JobWorker(type = ServiceTasks.MIRAVELO_REQUEST_CANCELLATION)
    public Map<String, Object> handle(@Variable String orderId) {
        boolean cancellationPossible = useCase.requestCancellation(new OrderId(orderId));
        return Map.of(FlowNodes.ServiceTaskRequestCancellation.Variables.CANCELLATION_POSSIBLE.getValue(), cancellationPossible);
    }
}
