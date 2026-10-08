package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository
import io.miragon.blueprint.application.port.outbound.NotificationPort
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.Runs
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class RejectApplicationServiceTest {

    private val repository = mockk<LeasingApplicationRepository>()
    private val notification = mockk<NotificationPort>()
    private val underTest = RejectApplicationService(repository = repository, notification = notification)

    @Test
    fun `reject notifies the customer and persists the rejected status`() {

        // given: an application in the repository
        val application = testLeasingApplication()
        every { repository.findById(application.id) } returns application
        every { notification.send(any(), application) } just Runs
        every { repository.save(any()) } answers { firstArg() }

        // when: the application is rejected
        underTest.reject(application.id)

        // then: the application is loaded, the customer notified and the application saved as REJECTED
        verify { repository.findById(application.id) }
        verify { notification.send(any(), application) }
        verify { repository.save(match { it.status == LeasingStatus.REJECTED }) }
        confirmVerified(repository, notification)
    }

    @Test
    fun `reject fails for an unknown application`() {

        // given: no application is stored under the id
        val id = ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
        every { repository.findById(id) } returns null

        // when / then: the lookup fails with an IllegalStateException, which the REST adapter maps to 404
        assertThatThrownBy { underTest.reject(id) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Unknown application $id")
        verify(exactly = 0) { repository.save(any()) }
    }
}
