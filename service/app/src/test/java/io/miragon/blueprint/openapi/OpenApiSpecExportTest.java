package io.miragon.blueprint.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.client.CamundaClient;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Exports the live OpenAPI contract to {@code openapi/openapi.json} at the repo root, so the committed
 * spec can never lie about the code. CI regenerates it and runs {@code git diff --exit-code} — the
 * output must be byte-for-byte deterministic, or that gate would flap.
 *
 * <p>Determinism is bought three ways: {@link SerializationFeature#ORDER_MAP_ENTRIES_BY_KEYS} sorts every
 * object key, a fixed two-space LF indenter keeps it stable across OSes, and a trailing newline
 * keeps POSIX tools happy.
 *
 * <p>This is not really an assertion test — it is a code generator wearing a JUnit costume so it runs
 * inside {@code ./mvnw verify} with a live application context. Zeebe is not needed to publish the
 * contract, so the engine's client autoconfiguration is excluded and a mock client is supplied,
 * which keeps this gate fast and broker-free. See the OpenAPI-contract ADR.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.main.web-application-type=servlet",
        "spring.autoconfigure.exclude=io.camunda.client.spring.configuration.CamundaAutoConfiguration",
    })
@ActiveProfiles("test")
class OpenApiSpecExportTest {

    @TestConfiguration
    static class MockEngineConfiguration {

        @Bean
        CamundaClient camundaClient() {
            return mock(CamundaClient.class);
        }
    }

    @Value("${local.server.port}")
    private int port;

    private final JsonMapper deterministicMapper =
        JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .defaultPrettyPrinter(
                new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n")))
            .build();

    @Test
    @DisplayName("exports the OpenAPI contract to openapi_openapi_json at the repo root")
    void exportsTheOpenApiContractToOpenapiOpenapiJsonAtTheRepoRoot() throws Exception {
        // given: the live spec served by springdoc
        String raw = fetch("http://localhost:" + port + "/v3/api-docs");
        assertThat(raw).isNotBlank();

        // when: it is re-serialised with sorted keys and a fixed indenter
        ObjectNode tree = (ObjectNode) deterministicMapper.readTree(raw);
        // Drop the `servers` block — springdoc fills it with the random test port, which would make
        // the drift gate flap. Any API consumer points at its own base URL anyway.
        tree.remove("servers");
        String pretty = deterministicMapper.writeValueAsString(tree) + "\n";

        // then: the result contains our /api paths and is written to the committed location
        assertThat(pretty).contains("\"/api/bike-leasing\"");
        Path target = repoRoot().resolve("openapi").resolve("openapi.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, pretty);
    }

    private String fetch(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    /**
     * The repo root: the nearest directory above the module's working directory that holds {@code .mvn} (the
     * marker Maven itself uses for the multi-module root), falling back to the outermost {@code pom.xml}.
     */
    private static Path repoRoot() {
        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path outermostPom = null;
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve(".mvn"))) {
                return dir;
            }
            if (Files.exists(dir.resolve("pom.xml"))) {
                outermostPom = dir;
            }
        }
        if (outermostPom == null) {
            throw new IllegalStateException("could not locate the repo root (no .mvn or pom.xml found above " + start + ")");
        }
        return outermostPom;
    }
}
