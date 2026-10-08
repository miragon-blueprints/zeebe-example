package io.miragon.blueprint.application.service;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.application.port.outbound.BikeDealerPort;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderBikeServiceTest {

    private final LeasingApplicationRepository repository = mock(LeasingApplicationRepository.class);
    private final BikeDealerPort bikeDealer = mock(BikeDealerPort.class);
    private final OrderBikeService underTest = new OrderBikeService(repository, bikeDealer);

    @Test
    @DisplayName("orderBike places an order when the dealer has the bike in stock")
    void orderBikePlacesAnOrderWhenTheDealerHasTheBikeInStock() {

        // given: an application whose bike is available at the dealer
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-900")).build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(bikeDealer.checkAvailability(application.bikeId())).thenReturn(true);
        when(bikeDealer.order(application.bikeId())).thenReturn(new OrderId("ORDER-900"));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when: the bike is ordered
        OrderBikeUseCase.Result result = underTest.orderBike(application.id());

        // then: the order id is returned and the application moves to ORDERED
        assertThat(result.bikeAvailable()).isTrue();
        assertThat(result.orderId()).isEqualTo(new OrderId("ORDER-900"));
        verify(bikeDealer).checkAvailability(application.bikeId());
        verify(bikeDealer).order(application.bikeId());
        verify(repository).save(argThat(it ->
            it.status() == LeasingStatus.ORDERED && new OrderId("ORDER-900").equals(it.orderId())));
        verifyNoMoreInteractions(bikeDealer);
    }

    @Test
    @DisplayName("orderBike reports an out-of-stock bike as unavailable and places no order")
    void orderBikeReportsAnOutOfStockBikeAsUnavailableAndPlacesNoOrder() {

        // given: an application whose bike is out of stock at the dealer
        LeasingApplication application = testLeasingApplication().bikeId(new BikeId("BIKE-OOS")).build();
        when(repository.findById(application.id())).thenReturn(Optional.of(application));
        when(bikeDealer.checkAvailability(application.bikeId())).thenReturn(false);

        // when: the bike is ordered
        OrderBikeUseCase.Result result = underTest.orderBike(application.id());

        // then: no order is placed and the bike is reported unavailable
        assertThat(result.bikeAvailable()).isFalse();
        assertThat(result.orderId()).isNull();
        verify(bikeDealer).checkAvailability(application.bikeId());
        verify(bikeDealer, never()).order(any());
        verify(repository, never()).save(any());
        verifyNoMoreInteractions(bikeDealer);
    }

    @Test
    @DisplayName("orderBike fails for an unknown application")
    void orderBikeFailsForAnUnknownApplication() {

        // given: no application is stored under the id
        ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        when(repository.findById(id)).thenReturn(Optional.empty());

        // when / then: the lookup fails with an IllegalStateException, which the REST adapter maps to 404
        assertThatThrownBy(() -> underTest.orderBike(id))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Unknown application " + id);
        verify(repository, never()).save(any());
    }
}
