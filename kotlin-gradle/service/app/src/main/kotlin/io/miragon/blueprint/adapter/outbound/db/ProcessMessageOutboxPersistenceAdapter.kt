package io.miragon.blueprint.adapter.outbound.db

import io.camunda.client.api.JsonMapper
import io.miragon.bpmn.runtime.MessageName
import io.miragon.bpmn.runtime.VariableName
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

@Component
class ProcessMessageOutboxPersistenceAdapter(
    private val repository: ProcessMessageJpaRepository,
    private val jsonMapper: JsonMapper,
    private val clock: Clock,
) {

    fun add(
        messageName: MessageName,
        correlationKey: String,
        variables: Map<VariableName, Any> = emptyMap(),
    ) {
        val message =
            ProcessMessageEntity(
                id = UUID.randomUUID(),
                messageName = messageName.value,
                correlationKey = correlationKey,
                variables = jsonMapper.toJson(variables.mapKeys { it.key.value }),
                createdAt = LocalDateTime.now(clock),
            )
        repository.save(message)
    }

    @Transactional
    fun relayOldest(publish: (ProcessMessageEntity) -> Unit): Boolean {
        val oldest = repository.findFirstByOrderByCreatedAtAsc() ?: return false
        publish(oldest)
        repository.delete(oldest)
        return true
    }
}
