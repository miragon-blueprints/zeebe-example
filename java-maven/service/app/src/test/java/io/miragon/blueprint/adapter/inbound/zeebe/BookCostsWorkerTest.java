package io.miragon.blueprint.adapter.inbound.zeebe;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BookCostsWorkerTest {

    private final BookCancellationCostsUseCase useCase = mock(BookCancellationCostsUseCase.class);

    private final BookCostsWorker worker = new BookCostsWorker(useCase);

    @Test
    @DisplayName("books the cancellation costs for the order")
    void booksTheCancellationCostsForTheOrder() {
        // when
        worker.handle("ORDER-1");

        // then
        verify(useCase).bookCosts(new OrderId("ORDER-1"));
    }
}
