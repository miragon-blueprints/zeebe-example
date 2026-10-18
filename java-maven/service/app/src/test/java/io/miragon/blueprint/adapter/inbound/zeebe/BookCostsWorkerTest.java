package io.miragon.blueprint.adapter.inbound.zeebe;

import static io.miragon.blueprint.adapter.inbound.zeebe.InMemoryProcessedJobStore.inMemoryIdempotentJobExecutor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BookCostsWorkerTest {

    private final BookCancellationCostsUseCase useCase = mock(BookCancellationCostsUseCase.class);

    private final BookCostsWorker worker = new BookCostsWorker(useCase, inMemoryIdempotentJobExecutor());

    private final ActivatedJob job = mock(ActivatedJob.class);

    @BeforeEach
    void stubJobKey() {
        when(job.getKey()).thenReturn(1L);
    }

    @Test
    @DisplayName("books the cancellation costs for the order")
    void booksTheCancellationCostsForTheOrder() {
        // when
        worker.handle(job, "ORDER-1");

        // then
        verify(useCase).bookCosts(new OrderId("ORDER-1"));
    }

    @Test
    @DisplayName("does not repeat the work on a repeated delivery of the same job")
    void doesNotRepeatTheWorkOnARepeatedDeliveryOfTheSameJob() {
        // when
        worker.handle(job, "ORDER-1");
        worker.handle(job, "ORDER-1");

        // then
        verify(useCase, times(1)).bookCosts(new OrderId("ORDER-1"));
    }
}
