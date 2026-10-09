package io.miragon.blueprint.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.miragon.blueprint.application.port.inbound.SelectAlternativeUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(SelectAlternativeController.class)
class SelectAlternativeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SelectAlternativeUseCase useCase;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("client clarifies an alternative bike")
    void clientClarifiesAnAlternativeBike() throws Exception {

        // given: a decision body and the application-id path variable
        String pathVar = "123e4567-e89b-12d3-a456-426614174000";
        Map<String, Object> body = Map.of("alternativeFound", true, "bikeId", "BIKE-ALT", "bikeModel", "Aero Road 700");
        MockHttpServletRequestBuilder operation =
            post("/api/bike-leasing/{applicationId}/clarify-alternative", pathVar)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body));

        // when: the request is performed
        MvcResult response = mockMvc.perform(operation).andReturn();

        // then: the use case is invoked with the mapped command and the response is 202 Accepted
        assertThat(response.getResponse().getStatus()).isEqualTo(202);
        verify(useCase).selectAlternative(
            new SelectAlternativeUseCase.Command(ApplicationId.of(pathVar), true, new BikeId("BIKE-ALT"), "Aero Road 700"));
        verifyNoMoreInteractions(useCase);
    }

    @Test
    @DisplayName("client reports that no alternative was found")
    void clientReportsThatNoAlternativeWasFound() throws Exception {

        // given: a decision body with no alternative and no bike details
        String pathVar = "123e4567-e89b-12d3-a456-426614174000";
        Map<String, Object> body = Map.of("alternativeFound", false);
        MockHttpServletRequestBuilder operation =
            post("/api/bike-leasing/{applicationId}/clarify-alternative", pathVar)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body));

        // when: the request is performed
        MvcResult response = mockMvc.perform(operation).andReturn();

        // then: the use case is invoked with a command carrying no alternative bike and the response is 202
        assertThat(response.getResponse().getStatus()).isEqualTo(202);
        verify(useCase).selectAlternative(
            new SelectAlternativeUseCase.Command(ApplicationId.of(pathVar), false, null, null));
        verifyNoMoreInteractions(useCase);
    }

    @Test
    @DisplayName("rejects an accepted alternative without a bike id with a 400 problem detail")
    void rejectsAnAcceptedAlternativeWithoutABikeIdWithA400ProblemDetail() throws Exception {

        // given: a decision body that accepts an alternative but names no bike
        String pathVar = "123e4567-e89b-12d3-a456-426614174000";
        Map<String, Object> body = Map.of("alternativeFound", true);
        MockHttpServletRequestBuilder operation =
            post("/api/bike-leasing/{applicationId}/clarify-alternative", pathVar)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body));

        // when: the request is performed
        MvcResult response = mockMvc.perform(operation).andReturn();

        // then: the request is refused before the use case is reached
        assertThat(response.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.getResponse().getContentType()).contains("application/problem+json");
        assertThat(response.getResponse().getContentAsString()).contains("An accepted alternative must name the bike");
        verifyNoMoreInteractions(useCase);
    }
}
