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

import io.miragon.blueprint.application.port.outbound.ContractPort;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.NotificationPort;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SendContractServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final ContractPort contract = mock(ContractPort.class);
    private final NotificationPort notification = mock(NotificationPort.class);
    private final SendContractService underTest = new SendContractService(repository, contract, notification);

    @Test
    @DisplayName("sendContract issues the contract, records its id on the application and notifies the customer")
    void sendContractIssuesTheContractRecordsItsIdOnTheApplicationAndNotifiesTheCustomer() {

        // given: an application whose contract the contract system will issue
        LeasingApplication application = testLeasingApplication().build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(contract.issueContract(application.id())).thenReturn(new ContractId("CONTRACT-1"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: the contract is sent
        underTest.sendContract(application.id());

        // then: the contract is issued, its id is stored on the application and the customer is asked to sign
        verify(repository).findById(application.id());
        verify(contract).issueContract(application.id());
        verify(repository).save(argThat(it -> new ContractId("CONTRACT-1").equals(it.contractId())));
        verify(notification).send(any(), eq(application));
        verifyNoMoreInteractions(repository, contract, notification);
    }

    @Test
    @DisplayName("sendContract fails for an unknown application")
    void sendContractFailsForAnUnknownApplication() {

        // given: no application is stored under the id
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        when(repository.findById(id)).thenReturn(Optional.empty());

        // when / then: the lookup fails with an IllegalStateException, which the REST adapter maps to 404
        assertThatThrownBy(() -> underTest.sendContract(id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unknown application " + id);
        verify(repository, never()).save(any());
    }
}
