package io.miragon.common.zeebe.engine;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import io.miragon.bpmn.runtime.MessageName;
import io.miragon.bpmn.runtime.ProcessId;
import io.miragon.bpmn.runtime.VariableName;
import io.miragon.common.zeebe.context.EventualConsistent;
import io.miragon.common.zeebe.context.StronglyConsistent;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProcessEngineApi {

    private final CamundaClient camundaClient;

    public ProcessEngineApi(CamundaClient camundaClient) {
        this.camundaClient = camundaClient;
    }

    /**
     * Use this method to start a process instance via an undefined start event
     * @param processId the id of the process to start
     * @return the key of the process instance
     */
    @StronglyConsistent
    public long startProcess(ProcessId processId) {
        return startProcess(processId, Map.of());
    }

    /**
     * Use this method to start a process instance via an undefined start event
     * @param processId the id of the process to start
     * @param variables the variables that should be passed to the process
     * @return the key of the process instance
     */
    @StronglyConsistent
    public long startProcess(ProcessId processId, Map<VariableName, Object> variables) {
        return camundaClient.newCreateInstanceCommand()
            .bpmnProcessId(processId.getValue())
            .latestVersion()
            .variables(byName(variables))
            .send()
            .join()
            .getProcessInstanceKey();
    }

    /**
     * Use this method to send a message to a running process instance.
     * @param messageName the name of the message that should be sent
     * @param correlationId an id that is used to identify the process instance
     */
    @StronglyConsistent
    public void sendMessage(MessageName messageName, String correlationId) {
        sendMessage(messageName, correlationId, Map.of());
    }

    /**
     * Use this method to send a message to a running process instance.
     * @param messageName the name of the message that should be sent
     * @param correlationId an id that is used to identify the process instance
     * @param variables the variables that should be passed to the process
     */
    @StronglyConsistent
    public void sendMessage(MessageName messageName, String correlationId, Map<VariableName, Object> variables) {
        camundaClient.newPublishMessageCommand()
            .messageName(messageName.getValue())
            .correlationKey(correlationId)
            .variables(byName(variables))
            .timeToLive(Duration.of(10, ChronoUnit.SECONDS))
            .send()
            .join();
    }

    /**
     * Use this method to search for process instances.
     * For usage in production I would recommend using filtering.
     * @return a list of all process instances
     */
    @EventualConsistent
    public List<ProcessInstance> searchForProcessInstances() {
        return camundaClient.newProcessInstanceSearchRequest().send().join().items();
    }

    private static Map<String, Object> byName(Map<VariableName, Object> variables) {
        Map<String, Object> byName = new LinkedHashMap<>();
        variables.forEach((name, value) -> byName.put(name.getValue(), value));
        return byName;
    }
}
