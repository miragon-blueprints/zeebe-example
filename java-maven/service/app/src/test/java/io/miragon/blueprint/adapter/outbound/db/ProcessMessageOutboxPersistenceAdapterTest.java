package io.miragon.blueprint.adapter.outbound.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.impl.CamundaObjectMapper;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.Messages;
import io.miragon.bpmn.runtime.VariableName;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({ProcessMessageOutboxPersistenceAdapter.class, ProcessMessageOutboxPersistenceAdapterTest.Beans.class})
class ProcessMessageOutboxPersistenceAdapterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 18, 12, 0);

    private static final UUID OLDER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID NEWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @TestConfiguration
    static class Beans {

        @Bean
        Clock clock() {
            return Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }

        @Bean
        JsonMapper jsonMapper() {
            return new CamundaObjectMapper();
        }
    }

    @Autowired
    private ProcessMessageOutboxPersistenceAdapter underTest;

    @Autowired
    private ProcessMessageJpaRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("adds a message with its variables as JSON")
    void addsAMessageWithItsVariablesAsJson() {
        // given: a message with one variable
        Map<VariableName, Object> variables =
            Map.of(FlowNodes.StartEventLeasingRequestReceived.Variables.APPLICATION_ID, "app-1");

        // when: it is added to the outbox and re-read from a cleared persistence context
        underTest.add(Messages.MIRAVELO_CONTRACT_SIGNED, "app-1", variables);
        entityManager.flush();
        entityManager.clear();

        // then: the stored message carries name, correlation key, variables and creation time
        ProcessMessageEntity stored = repository.findAll().getFirst();
        assertThat(repository.count()).isEqualTo(1);
        assertThat(stored.getMessageName()).isEqualTo(Messages.MIRAVELO_CONTRACT_SIGNED.getValue());
        assertThat(stored.getCorrelationKey()).isEqualTo("app-1");
        assertThat(stored.getVariables()).isEqualTo("{\"applicationId\":\"app-1\"}");
        assertThat(stored.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("adds a message without variables as an empty JSON object")
    void addsAMessageWithoutVariablesAsAnEmptyJsonObject() {
        // given: a message without variables
        // when: it is added to the outbox
        underTest.add(Messages.MIRAVELO_HANDOVER_REPORTED, "app-1");

        // then: its variables are stored as an empty JSON object
        assertThat(repository.findAll()).extracting(ProcessMessageEntity::getVariables).containsExactly("{}");
    }

    @Test
    @DisplayName("relays the oldest message and removes it from the outbox")
    void relaysTheOldestMessageAndRemovesItFromTheOutbox() {
        // given: two messages, the older one saved last
        entityManager.persist(message(NEWER_ID, NOW));
        entityManager.persist(message(OLDER_ID, NOW.minusSeconds(1)));
        entityManager.flush();
        entityManager.clear();

        // when: one message is relayed
        List<ProcessMessageEntity> published = new ArrayList<>();
        boolean relayed = underTest.relayOldest(published::add);
        entityManager.flush();

        // then: the older message was published and only the newer one is left
        assertThat(relayed).isTrue();
        assertThat(published).extracting(ProcessMessageEntity::getId).containsExactly(OLDER_ID);
        assertThat(repository.findAll()).extracting(ProcessMessageEntity::getId).containsExactly(NEWER_ID);
    }

    @Test
    @DisplayName("relays nothing when the outbox is empty")
    void relaysNothingWhenTheOutboxIsEmpty() {
        // given: an empty outbox
        List<ProcessMessageEntity> published = new ArrayList<>();

        // when / then: nothing is published and the caller is told so
        assertThat(underTest.relayOldest(published::add)).isFalse();
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("keeps the message when publishing fails")
    void keepsTheMessageWhenPublishingFails() {
        // given: one message in the outbox
        entityManager.persist(message(OLDER_ID, NOW));
        entityManager.flush();

        // when: publishing it fails
        assertThatThrownBy(() -> underTest.relayOldest(message -> {
            throw new IllegalStateException("engine unavailable");
        })).hasMessage("engine unavailable");

        // then: the message is still in the outbox
        assertThat(repository.findAll()).extracting(ProcessMessageEntity::getId).containsExactly(OLDER_ID);
    }

    private static ProcessMessageEntity message(UUID id, LocalDateTime createdAt) {
        return new ProcessMessageEntity(
            id,
            Messages.MIRAVELO_HANDOVER_REPORTED.getValue(),
            "app-1",
            "{}",
            createdAt);
    }
}
