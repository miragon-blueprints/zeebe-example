package io.miragon.blueprint.adapter.inbound.rest;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Documented escape hatch you should NOT need in production. This headless service serves its {@code /api}
 * endpoints on a single origin, so there is no CORS on the production path. This bean only activates under the
 * {@code dev} profile — enable it if you drive the API from a browser-based consumer running cross-origin in
 * development (e.g. a local SPA on {@code http://localhost:5173}). See CONTRIBUTING.md.
 */
@Configuration
@Profile("dev")
public class DevCorsConfiguration implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/api/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH");
    }
}
