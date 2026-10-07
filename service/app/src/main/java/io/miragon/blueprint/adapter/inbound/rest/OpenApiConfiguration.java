package io.miragon.blueprint.adapter.inbound.rest;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata for the generated contract served at {@code /v3/api-docs}.
 *
 * <p>This is cross-cutting web configuration and lives in {@code adapter.inbound.rest}, NOT in a separate
 * {@code config} package — the architecture tests ignore only direct members of the root package, so a
 * new {@code io.miragon.blueprint.config} package would fail the suite. The {@code Configuration} suffix is
 * whitelisted for this package in {@code NamingConventionArchitectureTest}.
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI bikeLeasingOpenApi() {
        return new OpenAPI()
            .info(
                new Info()
                    .title("MiraVelo Bike-Leasing API")
                    .version("1.0")
                    .description(
                        "Customer-portal and back-office endpoints for the MiraVelo bike-leasing "
                            + "process, backed by Camunda 8 / Zeebe."));
    }

    /**
     * Documents the bodiless endpoints ({@code ResponseEntity<Void>}) with an explicit, empty {@code content}
     * object instead of none — the shape the committed {@code openapi/openapi.json} has always published, so
     * the drift-gated contract stays unchanged.
     */
    @Bean
    public OperationCustomizer emptyContentForBodilessResponses() {
        return (operation, handlerMethod) -> {
            operation.getResponses().values().stream()
                .filter(response -> response.getContent() == null)
                .forEach(response -> response.setContent(new Content()));
            return operation;
        };
    }
}
