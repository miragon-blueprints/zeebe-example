package io.miragon.blueprint.adapter.inbound.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.miragon.blueprint.application.port.inbound.ReportHandoverUseCase;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(ReportHandoverController.class)
class GlobalExceptionConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportHandoverUseCase useCase;

    @Test
    @DisplayName("a missing clarification task maps to 409 with problem+json")
    void aMissingClarificationTaskMapsTo409WithProblemJson() throws Exception {

        // given: the clarification user task can no longer be located (token moved past the wait state)
        String pathVar = "123e4567-e89b-12d3-a456-426614174000";
        doThrow(new NoSuchElementException("No active clarify-alternative task for " + pathVar))
            .when(useCase).reportHandover(any());

        // when: the POST is performed
        MvcResult response =
            mockMvc
                .perform(post("/api/bike-leasing/{applicationId}/report-handover", pathVar))
                .andReturn();

        // then: 409 Conflict is returned as application/problem+json
        assertThat(response.getResponse().getStatus()).isEqualTo(409);
        assertThat(response.getResponse().getContentType()).contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    }

    @Test
    @DisplayName("an unknown application maps to 404 with problem+json")
    void anUnknownApplicationMapsTo404WithProblemJson() throws Exception {

        // given: the service cannot find the application
        String pathVar = "123e4567-e89b-12d3-a456-426614174000";
        doThrow(new IllegalStateException("Unknown application " + pathVar))
            .when(useCase).reportHandover(any());

        // when: the POST is performed
        MvcResult response =
            mockMvc
                .perform(post("/api/bike-leasing/{applicationId}/report-handover", pathVar))
                .andReturn();

        // then: 404 Not Found is returned as application/problem+json with type, title and detail
        assertThat(response.getResponse().getStatus()).isEqualTo(404);
        assertThat(response.getResponse().getContentType()).contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getResponse().getContentAsString())
            .contains(
                "\"type\":\"https://miravelo.example/problems/404\"",
                "\"title\":\"Resource not found\"",
                "\"detail\":\"Unknown application " + pathVar + "\"");
    }

    @Test
    @DisplayName("invalid input maps to 400 with problem+json")
    void invalidInputMapsTo400WithProblemJson() throws Exception {

        // given: a path variable that is not a UUID

        // when: the POST is performed
        MvcResult response =
            mockMvc
                .perform(post("/api/bike-leasing/{applicationId}/report-handover", "not-a-uuid"))
                .andReturn();

        // then: 400 Bad Request is returned as application/problem+json and the use case is never reached
        assertThat(response.getResponse().getStatus()).isEqualTo(400);
        assertThat(response.getResponse().getContentType()).contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getResponse().getContentAsString())
            .contains(
                "\"type\":\"https://miravelo.example/problems/400\"",
                "\"title\":\"Invalid request\"");
        verifyNoInteractions(useCase);
    }
}
