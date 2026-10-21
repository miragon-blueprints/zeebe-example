package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.camunda.client.api.response.ActivatedJob
import io.camunda.client.api.worker.JobClient
import io.camunda.client.exception.BpmnError
import io.camunda.client.exception.CamundaError
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.Errors
import io.miragon.blueprint.adapter.process.ServiceTasks
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.common.zeebe.idempotency.IdempotentJobExecutor
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Orders the bike from the dealer. The task is deployed as its own retryable job (`retries="3"` on the
 * BPMN task definition), so on failure we do not just let the job die: we fail it with a fixed 10s
 * backoff and a decremented retry count. This is the Zeebe equivalent of Camunda 7's
 * `R3/PT10S` retry cycle — the countdown is visible in Operate and, once the retries hit 0, Zeebe
 * automatically raises an incident on `serviceTask_orderBike`. It powers the reproducible incident
 * demo (bike id `BIKE-FAIL`, see the `06-incident-demo` Bruno collection); the poison-id logic itself
 * lives in the simulated dealer, which throws. An out-of-stock bike is no failure: it is signalled to
 * the process as the BPMN error `bikeUnavailable`, which the error boundary event on the service task
 * catches. `autoComplete` is off so we own the complete/throw/fail decision explicitly.
 */
@Component
class OrderBikeWorker(
    private val useCase: OrderBikeUseCase,
    private val idempotentJobs: IdempotentJobExecutor,
) {

    private val retryBackoff: Duration = Duration.ofSeconds(10)

    @JobWorker(type = ServiceTasks.MIRAVELO_ORDER_BIKE, autoComplete = [false])
    fun handle(client: JobClient, job: ActivatedJob, @Variable applicationId: String, @Variable bikeId: String) {
        try {
            val variables = idempotentJobs.runOnceWithVariables(job) { orderBike(applicationId, bikeId) }
            client.newCompleteCommand(job)
                .variables(variables)
                .send()
                .join()
        } catch (e: BpmnError) {
            client.newThrowErrorCommand(job)
                .errorCode(e.errorCode)
                .errorMessage(e.errorMessage)
                .send()
                .join()
        } catch (e: RuntimeException) {
            // Incident demo: the dealer "outage" throws. Fail the job with one fewer retry and a 10s
            // backoff so the retries count down visibly; at 0 retries Zeebe raises the incident.
            client.newFailCommand(job)
                .retries(job.retries - 1)
                .retryBackoff(retryBackoff)
                .errorMessage(e.message)
                .send()
                .join()
        }
    }

    private fun orderBike(applicationId: String, bikeId: String): Map<String, Any?> =
        try {
            val orderId = useCase.orderBike(ApplicationId.of(applicationId), BikeId(bikeId))
            mapOf(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value to orderId.value)
        } catch (e: BikeUnavailableException) {
            // Leaving the task through the error boundary event registers no order compensation.
            throw CamundaError.bpmnError(Errors.BIKE_UNAVAILABLE.code, e.message)
        }
}
