package io.miragon.blueprint.adapter.outbound.db

import io.camunda.client.api.JsonMapper
import io.camunda.client.impl.CamundaObjectMapper
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.Messages
import io.miragon.bpmn.runtime.VariableName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@DataJpaTest
@ActiveProfiles("test")
@Import(ProcessMessageOutboxPersistenceAdapter::class, ProcessMessageOutboxPersistenceAdapterTest.Beans::class)
class ProcessMessageOutboxPersistenceAdapterTest {

    @TestConfiguration
    class Beans {
        @Bean
        fun clock(): Clock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())

        @Bean
        fun jsonMapper(): JsonMapper = CamundaObjectMapper()
    }

    @Autowired
    private lateinit var underTest: ProcessMessageOutboxPersistenceAdapter

    @Autowired
    private lateinit var repository: ProcessMessageJpaRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Test
    fun `adds a message with its variables as JSON`() {

        // given: a message with one variable
        val variables = mapOf<VariableName, Any>(FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID to "app-1")

        // when: it is added to the outbox and re-read from a cleared persistence context
        underTest.add(Messages.MIRAVELO_CONTRACT_SIGNED, "app-1", variables)
        entityManager.flush()
        entityManager.clear()

        // then: the stored message carries name, correlation key, variables and creation time
        val stored = repository.findAll().single()
        assertThat(stored.messageName).isEqualTo(Messages.MIRAVELO_CONTRACT_SIGNED.value)
        assertThat(stored.correlationKey).isEqualTo("app-1")
        assertThat(stored.variables).isEqualTo("""{"applicationId":"app-1"}""")
        assertThat(stored.createdAt).isEqualTo(now)
    }

    @Test
    fun `adds a message without variables as an empty JSON object`() {

        // given: a message without variables
        // when: it is added to the outbox
        underTest.add(Messages.MIRAVELO_HANDOVER_REPORTED, "app-1")

        // then: its variables are stored as an empty JSON object
        assertThat(repository.findAll().map { it.variables }).containsExactly("{}")
    }

    @Test
    fun `relays the oldest message and removes it from the outbox`() {

        // given: two messages, the older one saved last
        entityManager.persist(message(id = newerId, createdAt = now))
        entityManager.persist(message(id = olderId, createdAt = now.minusSeconds(1)))
        entityManager.flush()
        entityManager.clear()

        // when: one message is relayed
        val published = mutableListOf<ProcessMessageEntity>()
        val relayed = underTest.relayOldest { published += it }
        entityManager.flush()

        // then: the older message was published and only the newer one is left
        assertThat(relayed).isTrue()
        assertThat(published.map { it.id }).containsExactly(olderId)
        assertThat(repository.findAll().map { it.id }).containsExactly(newerId)
    }

    @Test
    fun `relays nothing when the outbox is empty`() {

        // given: an empty outbox
        val published = mutableListOf<ProcessMessageEntity>()

        // when / then: nothing is published and the caller is told so
        assertThat(underTest.relayOldest { published += it }).isFalse()
        assertThat(published).isEmpty()
    }

    @Test
    fun `keeps the message when publishing fails`() {

        // given: one message in the outbox
        entityManager.persist(message(id = olderId, createdAt = now))
        entityManager.flush()

        // when: publishing it fails
        assertThatThrownBy { underTest.relayOldest { error("engine unavailable") } }
            .hasMessage("engine unavailable")

        // then: the message is still in the outbox
        assertThat(repository.findAll().map { it.id }).containsExactly(olderId)
    }

    private fun message(id: UUID, createdAt: LocalDateTime) =
        ProcessMessageEntity(
            id = id,
            messageName = Messages.MIRAVELO_HANDOVER_REPORTED.value,
            correlationKey = "app-1",
            variables = "{}",
            createdAt = createdAt,
        )

    private companion object {
        val now: LocalDateTime = LocalDateTime.of(2026, 10, 18, 12, 0)
        val olderId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val newerId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    }
}
