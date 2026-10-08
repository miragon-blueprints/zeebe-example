package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WithdrawApplicationServiceTest {

    private final LeasingProcess process = mock(LeasingProcess.class);
    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final WithdrawApplicationService underTest = new WithdrawApplicationService(process, repository);

    @Test
    @DisplayName("withdraw correlates the message and persists the WITHDRAWN status")
    void withdrawCorrelatesTheMessageAndPersistsTheWithdrawnStatus() {

        // given: a handed-over application
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        LeasingApplication application = testLeasingApplication().id(id).status(LeasingStatus.HANDED_OVER).build();
        when(repository.findById(id)).thenReturn(Optional.of(application));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: the application is withdrawn
        underTest.withdraw(id);

        // then: the message is correlated and the application is persisted with WITHDRAWN
        verify(process).correlateApplicationWithdrawn(id);
        verify(repository).findById(id);
        verify(repository).save(argThat(it -> it.status() == LeasingStatus.WITHDRAWN));
        verifyNoMoreInteractions(process, repository);
    }

    @Test
    @DisplayName("withdraw does not persist when correlation fails")
    void withdrawDoesNotPersistWhenCorrelationFails() {

        // given: a correlation that throws
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174001"));
        doThrow(new RuntimeException("no token")).when(process).correlateApplicationWithdrawn(id);

        // when / then: the exception propagates without touching the repository
        try {
            underTest.withdraw(id);
        } catch (RuntimeException ignored) {
        }

        verify(process).correlateApplicationWithdrawn(id);
        verify(repository, never()).findById(any());
        verify(repository, never()).save(any());
        verifyNoMoreInteractions(process, repository);
    }

    @Test
    @DisplayName("withdraw fails for an unknown application")
    void withdrawFailsForAnUnknownApplication() {

        // given: no application is stored under the id
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        when(repository.findById(id)).thenReturn(Optional.empty());

        // when / then: the lookup fails with an IllegalStateException, which the REST adapter maps to 404
        assertThatThrownBy(() -> underTest.withdraw(id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unknown application " + id);
        verify(repository, never()).save(any());
    }
}
