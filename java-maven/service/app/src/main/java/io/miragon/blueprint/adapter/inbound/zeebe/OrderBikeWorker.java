package io.miragon.blueprint.adapter.inbound.zeebe;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Orders the bike from the dealer. The task is deployed as its own retryable job ({@code retries="3"} on the
 * BPMN task definition), so on failure we do not just let the job die: we fail it with a fixed 10s
 * backoff and a decremented retry count. This is the Zeebe equivalent of Camunda 7's
 * {@code R3/PT10S} retry cycle — the countdown is visible in Operate and, once the retries hit 0, Zeebe
 * automatically raises an incident on {@code serviceTask_orderBike}. It powers the reproducible incident
 * demo (bike id {@code BIKE-FAIL}, see the {@code 06-incident-demo} Bruno collection); the poison-id logic itself
 * lives in the simulated dealer, which throws. {@code autoComplete} is off so we own the complete/fail
 * decision explicitly.
 */
@Component
public class OrderBikeWorker {

    private final OrderBikeUseCase useCase;

    private final Duration retryBackoff = Duration.ofSeconds(10);

    public OrderBikeWorker(OrderBikeUseCase useCase) {
        this.useCase = useCase;
    }

    @JobWorker(type = ServiceTasks.MIRAVELO_ORDER_BIKE, autoComplete = false)
    public void handle(JobClient client, ActivatedJob job, @Variable String applicationId) {
        try {
            OrderBikeUseCase.Result result = useCase.orderBike(ApplicationId.of(applicationId));
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put(
                FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(),
                result.orderId() != null ? result.orderId().value() : null);
            variables.put(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_AVAILABLE.getValue(), result.bikeAvailable());
            client.newCompleteCommand(job)
                .variables(variables)
                .send()
                .join();
        } catch (RuntimeException e) {
            // Incident demo: the dealer "outage" throws. Fail the job with one fewer retry and a 10s
            // backoff so the retries count down visibly; at 0 retries Zeebe raises the incident.
            client.newFailCommand(job)
                .retries(job.getRetries() - 1)
                .retryBackoff(retryBackoff)
                .errorMessage(e.getMessage())
                .send()
                .join();
        }
    }
}
