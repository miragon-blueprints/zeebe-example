package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidateApplicationServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final ValidateApplicationService underTest = new ValidateApplicationService(repository);

    @Test
    @DisplayName("validate loads a well-formed application without error")
    void validateLoadsAWellFormedApplicationWithoutError() {

        // given: a valid, solvent application in the repository
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));

        // when: the application is validated
        underTest.validate(application.id());

        // then: the application was loaded and accepted
        verify(repository).findById(application.id());
        verifyNoMoreInteractions(repository);
    }

    @Test
    @DisplayName("validate rejects an application without income")
    void validateRejectsAnApplicationWithoutIncome() {

        // given: an application with zero monthly net income
        LeasingApplication application = testLeasingApplication().monthlyNetIncome(0.0).build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));

        // when / then: validation surfaces the application as invalid
        assertThatThrownBy(() -> underTest.validate(application.id()))
            .isInstanceOf(ApplicationInvalidException.class);
    }

    @Test
    @DisplayName("validate fails for an unknown application")
    void validateFailsForAnUnknownApplication() {

        // given: no application is stored under the id
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        when(repository.findById(id)).thenReturn(Optional.empty());

        // when / then: the lookup fails with an IllegalStateException, which the REST adapter maps to 404
        assertThatThrownBy(() -> underTest.validate(id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unknown application " + id);
        verify(repository, never()).save(any());
    }
}
