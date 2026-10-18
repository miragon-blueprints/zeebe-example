package io.miragon.blueprint.adapter.outbound.db;

import io.camunda.client.api.JsonMapper;
import io.miragon.bpmn.runtime.MessageName;
import io.miragon.bpmn.runtime.VariableName;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProcessMessageOutboxPersistenceAdapter {

    private final ProcessMessageJpaRepository repository;

    private final JsonMapper jsonMapper;

    private final Clock clock;

    public ProcessMessageOutboxPersistenceAdapter(
        ProcessMessageJpaRepository repository,
        JsonMapper jsonMapper,
        Clock clock
    ) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    public void add(MessageName messageName, String correlationKey) {
        add(messageName, correlationKey, Map.of());
    }

    public void add(MessageName messageName, String correlationKey, Map<VariableName, Object> variables) {
        ProcessMessageEntity message = new ProcessMessageEntity(
            UUID.randomUUID(),
            messageName.getValue(),
            correlationKey,
            jsonMapper.toJson(byName(variables)),
            LocalDateTime.now(clock));
        repository.save(message);
    }

    @Transactional
    public boolean relayOldest(Consumer<ProcessMessageEntity> publish) {
        Optional<ProcessMessageEntity> oldest = repository.findFirstByOrderByCreatedAtAsc();
        oldest.ifPresent(message -> {
            publish.accept(message);
            repository.delete(message);
        });
        return oldest.isPresent();
    }

    private static Map<String, Object> byName(Map<VariableName, Object> variables) {
        Map<String, Object> byName = new LinkedHashMap<>();
        variables.forEach((name, value) -> byName.put(name.getValue(), value));
        return byName;
    }
}
