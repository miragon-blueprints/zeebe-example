package io.miragon.blueprint.adapter.inbound.rest

import com.ninjasquad.springmockk.MockkBean
import io.miragon.blueprint.application.port.inbound.ReportHandoverUseCase
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

@WebMvcTest(ReportHandoverController::class)
class GlobalExceptionConfigurationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var useCase: ReportHandoverUseCase

    @Test
    fun `a missing clarification task maps to 409 with problem+json`() {

        // given: the clarification user task can no longer be located (token moved past the wait state)
        val pathVar = "123e4567-e89b-12d3-a456-426614174000"
        every { useCase.reportHandover(any()) } throws
            NoSuchElementException("No active clarify-alternative task for $pathVar")

        // when: the POST is performed
        val response =
            mockMvc
                .perform(post("/api/bike-leasing/{applicationId}/report-handover", pathVar))
                .andReturn()

        // then: 409 Conflict is returned as application/problem+json
        assertThat(response.response.status).isEqualTo(409)
        assertThat(response.response.contentType).contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
    }

    @Test
    fun `an unknown application maps to 404 with problem+json`() {

        // given: the service cannot find the application
        val pathVar = "123e4567-e89b-12d3-a456-426614174000"
        every { useCase.reportHandover(any()) } throws IllegalStateException("Unknown application $pathVar")

        // when: the POST is performed
        val response =
            mockMvc
                .perform(post("/api/bike-leasing/{applicationId}/report-handover", pathVar))
                .andReturn()

        // then: 404 Not Found is returned as application/problem+json with type, title and detail
        assertThat(response.response.status).isEqualTo(404)
        assertThat(response.response.contentType).contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        assertThat(response.response.contentAsString)
            .contains(
                "\"type\":\"https://miravelo.example/problems/404\"",
                "\"title\":\"Resource not found\"",
                "\"detail\":\"Unknown application $pathVar\"",
            )
    }

    @Test
    fun `invalid input maps to 400 with problem+json`() {

        // given: a path variable that is not a UUID

        // when: the POST is performed
        val response =
            mockMvc
                .perform(post("/api/bike-leasing/{applicationId}/report-handover", "not-a-uuid"))
                .andReturn()

        // then: 400 Bad Request is returned as application/problem+json and the use case is never reached
        assertThat(response.response.status).isEqualTo(400)
        assertThat(response.response.contentType).contains(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        assertThat(response.response.contentAsString)
            .contains(
                "\"type\":\"https://miravelo.example/problems/400\"",
                "\"title\":\"Invalid request\"",
            )
        verify(exactly = 0) { useCase.reportHandover(any()) }
    }
}
