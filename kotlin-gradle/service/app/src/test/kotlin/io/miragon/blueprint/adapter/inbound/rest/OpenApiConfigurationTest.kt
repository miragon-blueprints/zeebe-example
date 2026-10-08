package io.miragon.blueprint.adapter.inbound.rest

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class OpenApiConfigurationTest {

    private val underTest = OpenApiConfiguration()

    @Test
    fun `publishes the API title, version and description`() {
        // when: the OpenAPI metadata is built
        val info = underTest.bikeLeasingOpenApi().info

        // then: it carries the contract's title, version and description
        assertThat(info.title).isEqualTo("MiraVelo Bike-Leasing API")
        assertThat(info.version).isEqualTo("1.0")
        assertThat(info.description)
            .isEqualTo("Customer-portal and back-office endpoints for the MiraVelo bike-leasing process, backed by Camunda 8 / Zeebe.")
    }
}
