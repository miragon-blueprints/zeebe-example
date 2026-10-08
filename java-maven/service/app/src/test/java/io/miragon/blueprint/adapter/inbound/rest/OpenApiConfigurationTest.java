package io.miragon.blueprint.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenApiConfigurationTest {

    private final OpenApiConfiguration underTest = new OpenApiConfiguration();

    @Test
    @DisplayName("publishes the API title, version and description")
    void publishesTheApiTitleVersionAndDescription() {
        // when: the OpenAPI metadata is built
        Info info = underTest.bikeLeasingOpenApi().getInfo();

        // then: it carries the contract's title, version and description
        assertThat(info.getTitle()).isEqualTo("MiraVelo Bike-Leasing API");
        assertThat(info.getVersion()).isEqualTo("1.0");
        assertThat(info.getDescription())
            .isEqualTo("Customer-portal and back-office endpoints for the MiraVelo bike-leasing process, backed by Camunda 8 / Zeebe.");
    }
}
