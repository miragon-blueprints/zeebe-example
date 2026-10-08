package io.miragon.common.zeebe.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.camunda.client.api.command.PublishMessageCommandStep1;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.response.PublishMessageResponse;
import io.miragon.bpmn.runtime.MessageName;
import io.miragon.bpmn.runtime.ProcessId;
import io.miragon.bpmn.runtime.VariableName;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcessEngineApiTest {

    private final CamundaClient camundaClient = mock(CamundaClient.class);
    private final ProcessEngineApi underTest = new ProcessEngineApi(camundaClient);

    @Test
    @DisplayName("should send message")
    @SuppressWarnings("unchecked")
    void shouldSendMessage() {

        VariableName dummyVar = new VariableName.Output("dummy");
        Map<VariableName, Object> testVariables = Map.of(dummyVar, "dummy");
        String correlationId = "correlationId";
        MessageName messageName = new MessageName("messageName");
        PublishMessageCommandStep1 step1 = mock(PublishMessageCommandStep1.class);
        PublishMessageCommandStep2 step2 = mock(PublishMessageCommandStep2.class);
        PublishMessageCommandStep3 step3 = mock(PublishMessageCommandStep3.class, RETURNS_SELF);
        CamundaFuture<PublishMessageResponse> future = mock(CamundaFuture.class);
        when(camundaClient.newPublishMessageCommand()).thenReturn(step1);
        when(step1.messageName(messageName.getValue())).thenReturn(step2);
        when(step2.correlationKey(correlationId)).thenReturn(step3);
        when(step3.send()).thenReturn(future);

        underTest.sendMessage(messageName, correlationId, testVariables);

        verify(step3).variables(Map.of(dummyVar.getValue(), "dummy"));
        verify(step3).timeToLive(Duration.of(10, ChronoUnit.SECONDS));
        verify(future).join();
    }

    @Test
    @DisplayName("should send start process message")
    @SuppressWarnings("unchecked")
    void shouldSendStartProcessMessage() {

        // given: mock the process instance creation
        VariableName dummyVar = new VariableName.Output("dummy");
        Map<VariableName, Object> testVariables = Map.of(dummyVar, "dummy");
        ProcessId processId = new ProcessId("my-process");
        long expectedKey = 12345L;
        ProcessInstanceEvent instanceEvent = mock(ProcessInstanceEvent.class);
        CreateProcessInstanceCommandStep1 step1 = mock(CreateProcessInstanceCommandStep1.class);
        CreateProcessInstanceCommandStep2 step2 = mock(CreateProcessInstanceCommandStep2.class);
        CreateProcessInstanceCommandStep3 plainCommand = mock(CreateProcessInstanceCommandStep3.class);
        CreateProcessInstanceCommandStep3 commandWithVariables = mock(CreateProcessInstanceCommandStep3.class);
        CamundaFuture<ProcessInstanceEvent> future = mock(CamundaFuture.class);
        when(camundaClient.newCreateInstanceCommand()).thenReturn(step1);
        when(step1.bpmnProcessId(processId.getValue())).thenReturn(step2);
        when(step2.latestVersion()).thenReturn(plainCommand);
        when(plainCommand.variables(anyMap())).thenReturn(commandWithVariables);
        when(commandWithVariables.send()).thenReturn(future);
        when(future.join()).thenReturn(instanceEvent);
        when(instanceEvent.getProcessInstanceKey()).thenReturn(expectedKey);

        // when: start the process
        long result = underTest.startProcess(processId, testVariables);

        // then: the process instance key should be returned
        assertThat(result).isEqualTo(expectedKey);
        verify(plainCommand).variables(Map.of(dummyVar.getValue(), "dummy"));
    }
}
