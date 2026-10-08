package io.miragon.blueprint.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
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

    @Test
    @DisplayName("gives bodiless responses an empty content object and leaves the others untouched")
    void givesBodilessResponsesAnEmptyContentObjectAndLeavesTheOthersUntouched() {
        // given: an operation with one bodiless and one JSON response
        Content json = new Content().addMediaType("application/json", new MediaType());
        ApiResponse bodiless = new ApiResponse().description("OK");
        ApiResponse withBody = new ApiResponse().description("OK").content(json);
        Operation operation =
            new Operation().responses(new ApiResponses().addApiResponse("200", bodiless).addApiResponse("201", withBody));

        // when: the customizer runs
        Operation customized = underTest.emptyContentForBodilessResponses().customize(operation, null);

        // then: only the bodiless response gains an (empty) content object
        assertThat(customized).isSameAs(operation);
        assertThat(bodiless.getContent()).isNotNull().isEmpty();
        assertThat(withBody.getContent()).isSameAs(json);
    }
}
