package io.miragon.blueprint.adapter.outbound.zeebe

import io.camunda.client.CamundaClient
import io.camunda.client.api.search.enums.UserTaskState
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.Messages
import io.miragon.blueprint.application.port.outbound.LeasingProcess
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.miragon.common.zeebe.engine.ProcessEngineApi
import org.springframework.stereotype.Component

/**
 * Drives the Camunda 8 / Zeebe engine. Zeebe has no business key, so the application id travels as a
 * process variable and is declared as the message subscription correlation key on every catch event;
 * messages are published with that id as their correlation key, and the user task is looked up by its
 * element id plus the instance's `applicationId` variable. The variable and element names come from
 * the typed process API generated from `bike-leasing.bpmn`.
 */
@Component
class LeasingProcessAdapter(
    private val engineApi: ProcessEngineApi,
    private val camundaClient: CamundaClient,
) : LeasingProcess {

    override fun submitRequest(application: LeasingApplication) {
        val start = FlowNodes.StartEventLeasingRequestReceived.Variables
        // Publishing the leasing-request message starts a new instance via the message start event.
        engineApi.sendMessage(
            messageName = Messages.MIRAVELO_LEASING_REQUEST_RECEIVED,
            correlationId = application.id.value.toString(),
            variables = mapOf(
                start.APPLICATION_ID to application.id.value.toString(),
                start.BIKE_ID to application.bikeId.value,
                start.MONTHLY_NET_INCOME to application.monthlyNetIncome,
                start.AGE to application.age,
            ),
        )
    }

    override fun correlateContractSigned(id: ApplicationId) =
        engineApi.sendMessage(Messages.MIRAVELO_CONTRACT_SIGNED, id.value.toString())

    override fun correlateHandoverReported(id: ApplicationId) =
        engineApi.sendMessage(Messages.MIRAVELO_HANDOVER_REPORTED, id.value.toString())

    override fun correlateApplicationWithdrawn(id: ApplicationId) =
        engineApi.sendMessage(Messages.MIRAVELO_APPLICATION_WITHDRAWN, id.value.toString())

    /**
     * Completes the `Clarify alternative with customer` user task via the Camunda client — the same
     * task a human could complete through its deployed Camunda Form in the Tasklist.
     */
    override fun completeAlternativeClarification(
        id: ApplicationId,
        alternativeFound: Boolean,
        bikeId: BikeId?,
    ) {
        val userTaskKey = findActiveClarifyAlternativeTask(id)
        val variables = buildMap<String, Any> {
            put(FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.value, alternativeFound)
            // The re-order reads the same start-injected bike variable, so reuse its name.
            bikeId?.let { put(FlowNodes.StartEventLeasingRequestReceived.Variables.BIKE_ID.value, it.value) }
        }
        camundaClient.newCompleteUserTaskCommand(userTaskKey).variables(variables).send().join()
    }

    private fun findActiveClarifyAlternativeTask(id: ApplicationId): Long {
        // Match by reading each open task's applicationId variable rather than filtering the search by
        // process-instance variables: on an Elasticsearch-backed cluster that variable filter is not
        // reliably in sync with the task index (the task is searchable before the filter matches), so
        // it would spuriously find nothing. The per-task variable read is the same path the task inbox
        // uses and stays consistent with the task's own visibility.
        val openTasks = camundaClient.newUserTaskSearchRequest()
            .filter { filter ->
                filter.state(UserTaskState.CREATED)
                filter.elementId(FlowNodes.UserTaskClarifyAlternative.id.value)
            }
            .send()
            .join()
            .items()
        return openTasks
            .firstOrNull { readApplicationId(it.userTaskKey) == id.value.toString() }
            ?.userTaskKey
            ?: throw NoSuchElementException(
                "No active '${FlowNodes.UserTaskClarifyAlternative.id.value}' task for application ${id.value}",
            )
    }

    /** Zeebe returns variable values as JSON, so a string comes back quoted — strip the quotes. */
    private fun readApplicationId(userTaskKey: Long): String? =
        camundaClient.newUserTaskVariableSearchRequest(userTaskKey)
            .send()
            .join()
            .items()
            .firstOrNull { it.name == FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.value }
            ?.value
            ?.removeSurrounding("\"")
}
