package io.miragon.blueprint.adapter.inbound.zeebe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestCancellationWorkerTest {

    private final RequestOrderCancellationUseCase useCase = mock(RequestOrderCancellationUseCase.class);

    private final RequestCancellationWorker worker = new RequestCancellationWorker(useCase);

    @Test
    @DisplayName("returns whether the cancellation is possible")
    void returnsWhetherTheCancellationIsPossible() {
        // given
        when(useCase.requestCancellation(new OrderId("ORDER-1"))).thenReturn(true);

        // when
        Map<String, Object> result = worker.handle("ORDER-1");

        // then
        assertThat(result).containsEntry("cancellationPossible", true);
    }
}
