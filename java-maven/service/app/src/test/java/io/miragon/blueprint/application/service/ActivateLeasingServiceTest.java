package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActivateLeasingServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final ActivateLeasingService underTest = new ActivateLeasingService(repository);

    @Test
    @DisplayName("activate loads the application, activates it, and persists the ACTIVE status")
    void activateLoadsTheApplicationActivatesItAndPersistsTheActiveStatus() {

        // given: a handed-over application
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        LeasingApplication application = testLeasingApplication().id(id).status(LeasingStatus.HANDED_OVER).build();
        when(repository.findById(id)).thenReturn(Optional.of(application));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: the leasing is activated
        underTest.activate(id);

        // then: the application is persisted with ACTIVE
        verify(repository).findById(id);
        verify(repository).save(argThat(it -> it.status() == LeasingStatus.ACTIVE));
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("activate fails for an unknown application")
    void activateFailsForAnUnknownApplication() {

        // given: no application is stored under the id
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        when(repository.findById(id)).thenReturn(Optional.empty());

        // when / then: the lookup fails with an IllegalStateException, which the REST adapter maps to 404
        assertThatThrownBy(() -> underTest.activate(id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unknown application " + id);
        verify(repository, never()).save(any());
    }
}
