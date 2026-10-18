package io.miragon.blueprint.adapter.inbound.zeebe;

import static io.miragon.blueprint.adapter.inbound.zeebe.InMemoryProcessedJobStore.inMemoryIdempotentJobExecutor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestCancellationWorkerTest {

    private final RequestOrderCancellationUseCase useCase = mock(RequestOrderCancellationUseCase.class);

    private final RequestCancellationWorker worker = new RequestCancellationWorker(useCase, inMemoryIdempotentJobExecutor());

    private final ActivatedJob job = mock(ActivatedJob.class);

    @BeforeEach
    void stubJobKey() {
        when(job.getKey()).thenReturn(1L);
    }

    @Test
    @DisplayName("returns whether the cancellation is possible")
    void returnsWhetherTheCancellationIsPossible() {
        // given
        when(useCase.requestCancellation(new OrderId("ORDER-1"))).thenReturn(true);

        // when
        Map<String, Object> result = worker.handle(job, "ORDER-1");

        // then
        assertThat(result).containsEntry("cancellationPossible", true);
    }

    @Test
    @DisplayName("answers a repeated delivery of the same job with the first answer without asking the dealer again")
    void answersARepeatedDeliveryOfTheSameJobWithTheFirstAnswerWithoutAskingTheDealerAgain() {
        // given
        when(useCase.requestCancellation(new OrderId("ORDER-1"))).thenReturn(true, false);
        worker.handle(job, "ORDER-1");

        // when
        Map<String, Object> result = worker.handle(job, "ORDER-1");

        // then
        assertThat(result).containsEntry("cancellationPossible", true);
        verify(useCase, times(1)).requestCancellation(new OrderId("ORDER-1"));
    }
}
