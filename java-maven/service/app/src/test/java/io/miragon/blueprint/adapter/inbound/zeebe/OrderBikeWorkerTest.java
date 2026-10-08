package io.miragon.blueprint.adapter.inbound.zeebe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1;
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.CompleteJobResponse;
import io.camunda.client.api.response.FailJobResponse;
import io.camunda.client.api.worker.JobClient;
import io.miragon.blueprint.adapter.outbound.dealer.BikeDealerAdapter.DealerUnavailableException;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderBikeWorkerTest {

    private final OrderBikeUseCase useCase = mock(OrderBikeUseCase.class);

    private final OrderBikeWorker worker = new OrderBikeWorker(useCase);

    private final JobClient client = mock(JobClient.class);

    private final ActivatedJob job = mock(ActivatedJob.class);

    // One mock per step of the fluent command chains, so each step can be verified on its own.
    private final CompleteJobCommandStep1 completeCommand = mock(CompleteJobCommandStep1.class);

    private final CamundaFuture<CompleteJobResponse> completeResponse = mock();

    private final FailJobCommandStep1 failCommand = mock(FailJobCommandStep1.class);

    private final FailJobCommandStep2 failCommandStep2 = mock(FailJobCommandStep2.class);

    private final CamundaFuture<FailJobResponse> failResponse = mock();

    @BeforeEach
    void stubCommandChains() {
        when(client.newCompleteCommand(job)).thenReturn(completeCommand);
        when(completeCommand.variables(anyMap())).thenReturn(completeCommand);
        when(completeCommand.send()).thenReturn(completeResponse);
        when(client.newFailCommand(job)).thenReturn(failCommand);
        when(failCommand.retries(anyInt())).thenReturn(failCommandStep2);
        when(failCommandStep2.retryBackoff(any())).thenReturn(failCommandStep2);
        when(failCommandStep2.errorMessage(any())).thenReturn(failCommandStep2);
        when(failCommandStep2.send()).thenReturn(failResponse);
    }

    @Test
    @DisplayName("completes the job with the order id and availability as output variables")
    void completesTheJobWithTheOrderIdAndAvailabilityAsOutputVariables() {
        // given
        UUID id = UUID.randomUUID();
        when(useCase.orderBike(ApplicationId.of(id.toString())))
            .thenReturn(new OrderBikeUseCase.Result(new OrderId("ORDER-1"), true));

        // when
        worker.handle(client, job, id.toString());

        // then
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.captor();
        verify(client).newCompleteCommand(job);
        verify(completeCommand).variables(variables.capture());
        verify(client, never()).newFailCommand(any(ActivatedJob.class));
        assertThat(variables.getValue())
            .containsEntry("orderId", "ORDER-1")
            .containsEntry("bikeAvailable", true);
    }

    @Test
    @DisplayName("completes the job reporting an unavailable bike with a null order id")
    void completesTheJobReportingAnUnavailableBikeWithANullOrderId() {
        // given
        UUID id = UUID.randomUUID();
        when(useCase.orderBike(any()))
            .thenReturn(new OrderBikeUseCase.Result(null, false));

        // when
        worker.handle(client, job, id.toString());

        // then
        ArgumentCaptor<Map<String, Object>> variables = ArgumentCaptor.captor();
        verify(client).newCompleteCommand(job);
        verify(completeCommand).variables(variables.capture());
        verify(client, never()).newFailCommand(any(ActivatedJob.class));
        assertThat(variables.getValue())
            .containsEntry("orderId", null)
            .containsEntry("bikeAvailable", false);
    }

    @Test
    @DisplayName("fails the job with a backoff when the dealer outage throws so retries count down to an incident")
    void failsTheJobWithABackoffWhenTheDealerOutageThrowsSoRetriesCountDownToAnIncident() {
        // given: the simulated dealer outage (BIKE-FAIL) propagates out of the use case
        UUID id = UUID.randomUUID();
        when(useCase.orderBike(any())).thenThrow(new DealerUnavailableException(new BikeId("BIKE-FAIL")));
        when(job.getRetries()).thenReturn(3);

        // when
        worker.handle(client, job, id.toString());

        // then: the job is failed (not completed) with one fewer retry, so Zeebe eventually raises an incident
        verify(client).newFailCommand(job);
        verify(failCommand).retries(2);
        verify(client, never()).newCompleteCommand(any(ActivatedJob.class));
    }
}
