package io.miragon.blueprint.domain.leasing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class ApplicationIdTest {

    @Test
    fun `parses the textual form of its UUID`() {
        // given/when: an application id is parsed from a UUID string
        val id = ApplicationId.of("123e4567-e89b-12d3-a456-426614174000")
        // then: it wraps exactly that UUID
        assertThat(id.value).isEqualTo(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
    }

    @Test
    fun `mints a fresh id on every call`() {
        // when/then: two new ids are distinct
        assertThat(ApplicationId.new()).isNotEqualTo(ApplicationId.new())
    }

    @Test
    fun `rejects a malformed id`() {
        // when/then: a value that is not a UUID is refused (the REST adapter maps this to 400)
        assertThatThrownBy { ApplicationId.of("not-a-uuid") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `renders its value the way error messages show it`() {
        // given/when: an application id is turned into text
        val id = ApplicationId.of("123e4567-e89b-12d3-a456-426614174000")
        // then: the text is the one the REST API exposes in a 404 detail
        assertThat(id.toString()).isEqualTo("ApplicationId(value=123e4567-e89b-12d3-a456-426614174000)")
    }
}
