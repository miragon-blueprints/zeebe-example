package io.miragon.blueprint.domain.bike;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BikeIdTest {

    @Test
    @DisplayName("exposes the wrapped bike id")
    void exposesTheWrappedBikeId() {
        // given/when: a bike id is created from a non-blank value
        BikeId bikeId = new BikeId("BIKE-900");
        // then: the raw value is exposed unchanged
        assertThat(bikeId.value()).isEqualTo("BIKE-900");
    }

    @Test
    @DisplayName("rejects a blank bike id")
    void rejectsABlankBikeId() {
        // when/then: a blank value is refused
        assertThatThrownBy(() -> new BikeId("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a bike id made of no-break spaces")
    void rejectsABikeIdMadeOfNoBreakSpaces() {
        // when/then: a value that only looks empty is refused, too
        assertThatThrownBy(() -> new BikeId("\u00A0\u202F"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
