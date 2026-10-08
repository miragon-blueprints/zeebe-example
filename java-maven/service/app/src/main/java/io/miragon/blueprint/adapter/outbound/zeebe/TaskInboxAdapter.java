package io.miragon.blueprint.adapter.outbound.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.application.port.outbound.TaskInboxPort;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Reads the open {@code Clarify alternative with customer} tasks from Zeebe's task list and translates them
 * into the domain's business key. Zeebe has no business key, so the application id travels as a process
 * variable: for each open task we read its {@code applicationId} variable and its creation date. It never
 * leaks an engine task id upward — the inbox lists cases, and cases are resolved through the domain,
 * correlated by id.
 */
@Component
public class TaskInboxAdapter implements TaskInboxPort {

    private final CamundaClient camundaClient;

    private final String applicationIdVariable = FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID.getValue();

    public TaskInboxAdapter(CamundaClient camundaClient) {
        this.camundaClient = camundaClient;
    }

    @Override
    public List<TaskInboxPort.OpenClarification> findOpenClarifications() {
        List<UserTask> openTasks = camundaClient.newUserTaskSearchRequest()
            .filter(filter -> {
                filter.state(UserTaskState.CREATED);
                filter.elementId(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID);
            })
            .send()
            .join()
            .items();
        return openTasks.stream()
            .flatMap(task -> readApplicationId(task.getUserTaskKey())
                .map(applicationId -> new TaskInboxPort.OpenClarification(
                    ApplicationId.of(applicationId),
                    task.getCreationDate().toLocalDateTime()))
                .stream())
            .toList();
    }

    /**
     * Reads the instance's {@code applicationId} from the task's scope. Zeebe returns variable values as
     * JSON, so a string comes back quoted — strip the quotes to recover the raw id.
     */
    private Optional<String> readApplicationId(long userTaskKey) {
        return camundaClient.newUserTaskVariableSearchRequest(userTaskKey)
            .send()
            .join()
            .items()
            .stream()
            .filter(variable -> applicationIdVariable.equals(variable.getName()))
            .findFirst()
            .map(Variable::getValue)
            .map(TaskInboxAdapter::removeSurroundingQuotes);
    }

    private static String removeSurroundingQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
