package io.miragon.blueprint.adapter.outbound.zeebe

import io.camunda.client.CamundaClient
import io.camunda.client.api.search.enums.UserTaskState
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.application.port.outbound.TaskInboxPort
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

/**
 * Reads the open `Clarify alternative with customer` tasks from Zeebe's task list and translates them
 * into the domain's business key. Zeebe has no business key, so the application id travels as a process
 * variable: for each open task we read its `applicationId` variable and its creation date. It never
 * leaks an engine task id upward — the inbox lists cases, and cases are resolved through the domain,
 * correlated by id.
 */
@Component
class TaskInboxAdapter(
    private val camundaClient: CamundaClient,
) : TaskInboxPort {

    private val applicationIdVariable = FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.value

    override fun findOpenClarifications(): List<TaskInboxPort.OpenClarification> {
        val openTasks = camundaClient.newUserTaskSearchRequest()
            .filter { filter ->
                filter.state(UserTaskState.CREATED)
                filter.elementId(FlowNodes.UserTaskClarifyAlternative.id.value)
            }
            .send()
            .join()
            .items()
        return openTasks.mapNotNull { task ->
            val applicationId = readApplicationId(task.userTaskKey) ?: return@mapNotNull null
            TaskInboxPort.OpenClarification(
                applicationId = ApplicationId.of(applicationId),
                waitingSince = task.creationDate.toLocalDateTime(),
            )
        }
    }

    /**
     * Reads the instance's `applicationId` from the task's scope. Zeebe returns variable values as
     * JSON, so a string comes back quoted — strip the quotes to recover the raw id.
     */
    private fun readApplicationId(userTaskKey: Long): String? =
        camundaClient.newUserTaskVariableSearchRequest(userTaskKey)
            .send()
            .join()
            .items()
            .firstOrNull { it.name == applicationIdVariable }
            ?.value
            ?.removeSurrounding("\"")
}
