package io.miragon.blueprint.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.BOOLEAN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.application.port.inbound.ListBikesQuery;
import io.miragon.blueprint.application.port.outbound.BikeDealerPort;
import io.miragon.blueprint.application.port.outbound.BikePortfolioRepository;
import io.miragon.blueprint.domain.bike.Bike;
import io.miragon.blueprint.domain.bike.BikeId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ListBikesServiceTest {

    private final BikePortfolioRepository bikePortfolio = mock(BikePortfolioRepository.class);
    private final BikeDealerPort bikeDealer = mock(BikeDealerPort.class);
    private final ListBikesService underTest = new ListBikesService(bikePortfolio, bikeDealer);

    @Test
    @DisplayName("enriches each catalogue bike with its dealer availability")
    void enrichesEachCatalogueBikeWithItsDealerAvailability() {
        // given: a catalogue (already ordered by the portfolio) where one bike is out of stock
        when(bikePortfolio.findAll())
            .thenReturn(
                List.of(
                    new Bike(new BikeId("BIKE-900"), "Gravel Explorer 900"),
                    new Bike(new BikeId("BIKE-OOS"), "Mountain Trail 600")));
        when(bikeDealer.checkAvailability(new BikeId("BIKE-900"))).thenReturn(true);
        when(bikeDealer.checkAvailability(new BikeId("BIKE-OOS"))).thenReturn(false);

        // when: the catalogue is listed
        List<ListBikesQuery.Item> result = underTest.all();

        // then: the portfolio's order is preserved and each item's availability comes from the dealer
        assertThat(result).extracting(item -> item.bikeId().value()).containsExactly("BIKE-900", "BIKE-OOS");
        assertThat(result)
            .filteredOn(item -> item.bikeId().value().equals("BIKE-900"))
            .singleElement()
            .extracting(ListBikesQuery.Item::available, BOOLEAN)
            .isTrue();
        assertThat(result)
            .filteredOn(item -> item.bikeId().value().equals("BIKE-OOS"))
            .singleElement()
            .extracting(ListBikesQuery.Item::available, BOOLEAN)
            .isFalse();
    }

    @Test
    @DisplayName("returns an empty list for an empty catalogue")
    void returnsAnEmptyListForAnEmptyCatalogue() {
        // given: no bikes
        when(bikePortfolio.findAll()).thenReturn(List.of());

        // when / then: nothing is listed and the dealer is never consulted
        assertThat(underTest.all()).isEmpty();
        verifyNoInteractions(bikeDealer);
    }
}
