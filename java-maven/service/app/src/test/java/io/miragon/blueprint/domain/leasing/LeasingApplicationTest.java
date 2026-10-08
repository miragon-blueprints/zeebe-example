package io.miragon.blueprint.domain.leasing;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeasingApplicationTest {

    @Test
    @DisplayName("exposes the applicant's age and monthly net income")
    void exposesTheApplicantsAgeAndMonthlyNetIncome() {
        // given: an application built with a specific age and income
        LeasingApplication application = testLeasingApplication().age(40).monthlyNetIncome(4200.0).build();
        // then: both fields are exposed unchanged
        assertThat(application.age()).isEqualTo(40);
        assertThat(application.monthlyNetIncome()).isEqualTo(4200.0);
    }

    @Test
    @DisplayName("documentOrder attaches the order id and moves to ORDERED")
    void documentOrderAttachesTheOrderIdAndMovesToOrdered() {
        // given: a received application
        LeasingApplication application = testLeasingApplication().status(LeasingStatus.RECEIVED).build();
        // when: a bike order is attached
        LeasingApplication ordered = application.documentOrder(new OrderId("ORDER-1"));
        // then: the order id is set and the status is ORDERED
        assertThat(ordered).isEqualTo(
            testLeasingApplication().orderId(new OrderId("ORDER-1")).status(LeasingStatus.ORDERED).build());
    }

    @Test
    @DisplayName("selectAlternative swaps in the newly chosen bike")
    void selectAlternativeSwapsInTheNewlyChosenBike() {
        // given: an application whose requested bike was unavailable
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-900")).build();
        // when: the customer accepts an alternative bike
        LeasingApplication updated = application.selectAlternative(new BikeId("BIKE-ALT"));
        // then: the chosen bike is recorded
        assertThat(updated.bikeId()).isEqualTo(new BikeId("BIKE-ALT"));
    }

    @Test
    @DisplayName("withContract records the issued contract")
    void withContractRecordsTheIssuedContract() {
        // given: an application without a contract yet
        LeasingApplication application = testLeasingApplication().build();
        // when: the contract system issues a contract
        LeasingApplication updated = application.withContract(new ContractId("CONTRACT-1"));
        // then: the contract id is recorded
        assertThat(updated.contractId()).isEqualTo(new ContractId("CONTRACT-1"));
    }

    @Test
    @DisplayName("reject changes the status to REJECTED")
    void rejectChangesTheStatusToRejected() {
        // given: a received application
        LeasingApplication application = testLeasingApplication().build();
        // when: it is rejected
        LeasingApplication rejected = application.reject();
        // then: the status is REJECTED
        assertThat(rejected.status()).isEqualTo(LeasingStatus.REJECTED);
    }

    @Test
    @DisplayName("validate fails when the monthly net income is zero")
    void validateFailsWhenTheMonthlyNetIncomeIsZero() {
        // given: an application without income
        LeasingApplication application = testLeasingApplication().monthlyNetIncome(0.0).build();
        // when / then: validation reports the application as invalid
        assertThatThrownBy(application::validate).isInstanceOf(ApplicationInvalidException.class);
    }

    @Test
    @DisplayName("validate returns the application unchanged when it has income")
    void validateReturnsTheApplicationUnchangedWhenItHasIncome() {
        // given: a solvent application
        LeasingApplication application = testLeasingApplication().monthlyNetIncome(3500.0).build();
        // when / then: validation passes and hands back the same application
        assertThat(application.validate()).isSameAs(application);
    }
}
