package io.miragon.blueprint.application.service

import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository
import io.miragon.blueprint.application.port.outbound.NotificationPort
import io.miragon.blueprint.domain.leasing.ApplicationId
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

class SendSignatureReminderServiceTest {

    private val repository = mockk<LeasingApplicationRepository>()
    private val notification = mockk<NotificationPort>()
    private val underTest = SendSignatureReminderService(repository = repository, notification = notification)

    @Test
    fun `sendSignatureReminder loads the application and reminds the customer`() {

        // given: an application in the repository
        val application = testLeasingApplication()
        every { repository.findById(application.id) } returns application
        every { notification.send(any(), application) } just Runs

        // when: the signature reminder is sent
        underTest.sendSignatureReminder(application.id)

        // then: the application is loaded and the customer is reminded
        verify { repository.findById(application.id) }
        verify { notification.send(any(), application) }
        confirmVerified(repository, notification)
    }

    @Test
    fun `sendSignatureReminder fails for an unknown application`() {

        // given: no application is stored under the id
        val id = ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
        every { repository.findById(id) } returns null

        // when / then: the lookup fails with an IllegalStateException, which the REST adapter maps to 404
        assertThatThrownBy { underTest.sendSignatureReminder(id) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Unknown application $id")
        verify(exactly = 0) { repository.save(any()) }
    }
}
