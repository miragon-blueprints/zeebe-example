package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SendCancellationConfirmationServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final SendCancellationConfirmationService underTest =
        new SendCancellationConfirmationService(repository, notification);

    @Test
    @DisplayName("sendCancellationConfirmation loads the application, notifies the customer and persists the cancelled status")
    void sendCancellationConfirmationLoadsTheApplicationNotifiesTheCustomerAndPersistsTheCancelledStatus() {

        // given: an application in the repository
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: the cancellation confirmation is sent
        underTest.sendCancellationConfirmation(application.id());

        // then: the application is loaded, the customer informed and the application saved as CANCELLED
        verify(repository).findById(application.id());
        verify(notification).send(any(), eq(application));
        verify(repository).save(argThat(it -> it.status() == LeasingStatus.CANCELLED));
        verifyNoMoreInteractions(repository, notification);
    }

    @Test
    @DisplayName("sendCancellationConfirmation fails for an unknown application")
    void sendCancellationConfirmationFailsForAnUnknownApplication() {

        // given: no application is stored under the id
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        when(repository.findById(id)).thenReturn(Optional.empty());

        // when / then: the lookup fails with an IllegalStateException, which the REST adapter maps to 404
        assertThatThrownBy(() -> underTest.sendCancellationConfirmation(id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unknown application " + id);
        verify(repository, never()).save(any());
    }
}
