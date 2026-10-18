package io.miragon.blueprint.adapter.outbound.zeebe

import io.camunda.client.CamundaClient
import io.miragon.blueprint.adapter.outbound.db.ProcessMessageEntity
import io.miragon.blueprint.adapter.outbound.db.ProcessMessageOutboxPersistenceAdapter
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class ProcessMessageRelayAdapter(
    private val outbox: ProcessMessageOutboxPersistenceAdapter,
    private val camundaClient: CamundaClient,
) {

    private val log = KotlinLogging.logger {}

    @Scheduled(fixedDelay = 200)
    fun relayPendingMessages() {
        try {
            do {
                val relayed = outbox.relayOldest(::publish)
            } while (relayed)
        } catch (exception: Exception) {
            log.warn { "Publishing process messages failed, retrying on the next run: ${exception.message}" }
        }
    }

    private fun publish(message: ProcessMessageEntity) {
        camundaClient.newPublishMessageCommand()
            .messageName(message.messageName)
            .correlationKey(message.correlationKey)
            .messageId(message.id.toString())
            .variables(message.variables)
            .timeToLive(Duration.ofSeconds(10))
            .send()
            .join()
    }
}
