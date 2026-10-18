package io.miragon.blueprint.adapter.inbound.zeebe;

import static io.miragon.blueprint.adapter.inbound.zeebe.InMemoryProcessedJobStore.inMemoryIdempotentJobExecutor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ActivatedJob;
import io.miragon.blueprint.application.port.inbound.SendContractUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SendContractWorkerTest {

    private final SendContractUseCase useCase = mock(SendContractUseCase.class);

    private final SendContractWorker worker = new SendContractWorker(useCase, inMemoryIdempotentJobExecutor());

    private final ActivatedJob job = mock(ActivatedJob.class);

    @BeforeEach
    void stubJobKey() {
        when(job.getKey()).thenReturn(1L);
    }

    @Test
    @DisplayName("delegates to the use case for the application")
    void delegatesToTheUseCaseForTheApplication() {
        // given
        UUID id = UUID.randomUUID();

        // when
        worker.handle(job, id.toString());

        // then
        verify(useCase).sendContract(ApplicationId.of(id.toString()));
    }

    @Test
    @DisplayName("does not repeat the work on a repeated delivery of the same job")
    void doesNotRepeatTheWorkOnARepeatedDeliveryOfTheSameJob() {
        // given
        UUID id = UUID.randomUUID();

        // when
        worker.handle(job, id.toString());
        worker.handle(job, id.toString());

        // then
        verify(useCase, times(1)).sendContract(ApplicationId.of(id.toString()));
    }
}
