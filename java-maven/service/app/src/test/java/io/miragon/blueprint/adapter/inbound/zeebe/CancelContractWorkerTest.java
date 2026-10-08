package io.miragon.blueprint.adapter.inbound.zeebe;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.miragon.blueprint.application.port.inbound.CancelContractUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelContractWorkerTest {

    private final CancelContractUseCase useCase = mock(CancelContractUseCase.class);

    private final CancelContractWorker worker = new CancelContractWorker(useCase);

    @Test
    @DisplayName("delegates to the use case for the application")
    void delegatesToTheUseCaseForTheApplication() {
        // given
        UUID id = UUID.randomUUID();

        // when
        worker.handle(id.toString());

        // then
        verify(useCase).cancelContract(ApplicationId.of(id.toString()));
    }
}
