package io.miragon.blueprint.adapter.inbound.zeebe;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SendReminderMailWorkerTest {

    private final SendSignatureReminderUseCase useCase = mock(SendSignatureReminderUseCase.class);

    private final SendReminderMailWorker worker = new SendReminderMailWorker(useCase);

    @Test
    @DisplayName("delegates to the use case for the application")
    void delegatesToTheUseCaseForTheApplication() {
        // given
        UUID id = UUID.randomUUID();

        // when
        worker.handle(id.toString());

        // then
        verify(useCase).sendSignatureReminder(ApplicationId.of(id.toString()));
    }
}
