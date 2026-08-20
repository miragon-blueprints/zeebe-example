package io.miragon.blueprint.adapter.inbound.rest

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * OpenAPI metadata for the generated contract served at `/v3/api-docs`.
 *
 * This is cross-cutting web configuration and lives in `adapter.inbound.rest`, NOT in a separate
 * `config` package — the architecture tests ignore only direct members of the root package, so a
 * new `io.miragon.blueprint.config` package would fail the suite. The `Configuration` suffix is
 * whitelisted for this package in `NamingConventionArchitectureTest`.
 */
@Configuration
class OpenApiConfiguration {

    @Bean
    fun bikeLeasingOpenApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("MiraVelo Bike-Leasing API")
                    .version("1.0")
                    .description(
                        "Customer-portal and back-office endpoints for the MiraVelo bike-leasing " +
                            "process, backed by Camunda 8 / Zeebe.",
                    ),
            )
}
