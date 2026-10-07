package io.miragon.blueprint.adapter.inbound.zeebe;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1.ThrowErrorCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.response.ThrowErrorResponse;
import io.camunda.client.api.worker.JobClient;
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidateApplicationWorkerTest {

    private final ValidateApplicationUseCase useCase = mock(ValidateApplicationUseCase.class);

    private final ValidateApplicationWorker worker = new ValidateApplicationWorker(useCase);

    private final JobClient client = mock(JobClient.class);

    private final ActivatedJob job = mock(ActivatedJob.class);

    // One mock per step of the fluent command chains, so each step can be verified on its own.
    private final CompleteJobCommandStep1 completeCommand = mock(CompleteJobCommandStep1.class);

    private final CamundaFuture<CompleteJobResponse> completeResponse = mock();

    private final ThrowErrorCommandStep1 throwErrorCommand = mock(ThrowErrorCommandStep1.class);

    private final ThrowErrorCommandStep2 throwErrorCommandStep2 = mock(ThrowErrorCommandStep2.class);

    private final CamundaFuture<ThrowErrorResponse> throwErrorResponse = mock();

    @BeforeEach
    void stubCommandChains() {
        when(client.newCompleteCommand(job)).thenReturn(completeCommand);
        when(completeCommand.send()).thenReturn(completeResponse);
        when(client.newThrowErrorCommand(job)).thenReturn(throwErrorCommand);
        when(throwErrorCommand.errorCode(any())).thenReturn(throwErrorCommandStep2);
        when(throwErrorCommandStep2.errorMessage(any())).thenReturn(throwErrorCommandStep2);
        when(throwErrorCommandStep2.send()).thenReturn(throwErrorResponse);
    }

    @Test
    @DisplayName("completes the job for a valid application")
    void completesTheJobForAValidApplication() {
        // given
        UUID id = UUID.randomUUID();
        doNothing().when(useCase).validate(ApplicationId.of(id.toString()));

        // when
        worker.handle(client, job, id.toString());

        // then
        verify(useCase).validate(ApplicationId.of(id.toString()));
        verify(client).newCompleteCommand(job);
        verify(client, never()).newThrowErrorCommand(any(ActivatedJob.class));
    }

    @Test
    @DisplayName("throws the applicationInvalid BPMN error for an invalid application")
    void throwsTheApplicationInvalidBpmnErrorForAnInvalidApplication() {
        // given
        UUID id = UUID.randomUUID();
        doThrow(new ApplicationInvalidException(ApplicationId.of(id.toString()), "too young"))
            .when(useCase).validate(any());

        // when
        worker.handle(client, job, id.toString());

        // then
        verify(client).newThrowErrorCommand(job);
        verify(client, never()).newCompleteCommand(any(ActivatedJob.class));
    }
}
