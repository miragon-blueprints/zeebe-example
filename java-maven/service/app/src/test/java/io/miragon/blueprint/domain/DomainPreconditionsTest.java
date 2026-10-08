package io.miragon.blueprint.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DomainPreconditionsTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n", " ", " ", " ", "  \t"})
    @DisplayName("requireNotBlank rejects null, empty and whitespace-only values, including no-break spaces")
    void requireNotBlankRejectsNullEmptyAndWhitespaceOnlyValues(String value) {
        // when / then: the value is refused with the caller's message
        assertThatThrownBy(() -> DomainPreconditions.requireNotBlank(value, "must not be blank"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("must not be blank");
    }

    @Test
    @DisplayName("requireNotBlank returns a value with visible characters unchanged")
    void requireNotBlankReturnsAValueWithVisibleCharactersUnchanged() {
        // when / then: surrounding whitespace is kept, not trimmed
        assertThat(DomainPreconditions.requireNotBlank(" BIKE-900 ", "must not be blank")).isEqualTo(" BIKE-900 ");
    }

    @Test
    @DisplayName("requireNonNull rejects null with the caller's message")
    void requireNonNullRejectsNullWithTheCallersMessage() {
        // when / then: null is refused
        assertThatThrownBy(() -> DomainPreconditions.requireNonNull(null, "must not be null"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("must not be null");
    }

    @Test
    @DisplayName("requireNonNull returns a present value unchanged")
    void requireNonNullReturnsAPresentValueUnchanged() {
        // given: any non-null value
        Object value = new Object();
        // when / then: the very same instance comes back
        assertThat(DomainPreconditions.requireNonNull(value, "must not be null")).isSameAs(value);
    }
}
