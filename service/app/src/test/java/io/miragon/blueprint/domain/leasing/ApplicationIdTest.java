package io.miragon.blueprint.domain.leasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApplicationIdTest {

    @Test
    @DisplayName("parses the textual form of its UUID")
    void parsesTheTextualFormOfItsUuid() {
        // given/when: an application id is parsed from a UUID string
        ApplicationId id = ApplicationId.of("123e4567-e89b-12d3-a456-426614174000");
        // then: it wraps exactly that UUID
        assertThat(id.value()).isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    @DisplayName("mints a fresh id on every call")
    void mintsAFreshIdOnEveryCall() {
        // when/then: two new ids are distinct
        assertThat(ApplicationId.newId()).isNotNull().isNotEqualTo(ApplicationId.newId());
    }

    @Test
    @DisplayName("rejects a malformed id")
    void rejectsAMalformedId() {
        // when/then: a value that is not a UUID is refused (the REST adapter maps this to 400)
        assertThatThrownBy(() -> ApplicationId.of("not-a-uuid"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a missing UUID")
    void rejectsAMissingUuid() {
        // when/then: null is refused
        assertThatThrownBy(() -> new ApplicationId(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("ApplicationId must not be null");
    }
}
