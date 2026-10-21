package io.miragon.blueprint.adapter.outbound.zeebe;

import io.camunda.client.CamundaClient;
import io.miragon.blueprint.adapter.outbound.db.ProcessMessageEntity;
import io.miragon.blueprint.adapter.outbound.db.ProcessMessageOutboxPersistenceAdapter;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProcessMessageRelayAdapter {

    private static final Logger log = LoggerFactory.getLogger(ProcessMessageRelayAdapter.class);

    private final ProcessMessageOutboxPersistenceAdapter outbox;

    private final CamundaClient camundaClient;

    public ProcessMessageRelayAdapter(ProcessMessageOutboxPersistenceAdapter outbox, CamundaClient camundaClient) {
        this.outbox = outbox;
        this.camundaClient = camundaClient;
    }

    @Scheduled(fixedDelay = 200)
    public void relayPendingMessages() {
        try {
            boolean relayed;
            do {
                relayed = outbox.relayOldest(this::publish);
            } while (relayed);
        } catch (Exception exception) {
            log.warn("Publishing process messages failed, retrying on the next run: {}", exception.getMessage());
        }
    }

    private void publish(ProcessMessageEntity message) {
        camundaClient.newPublishMessageCommand()
            .messageName(message.getMessageName())
            .correlationKey(message.getCorrelationKey())
            .messageId(message.getId().toString())
            .variables(message.getVariables())
            .timeToLive(Duration.ofSeconds(10))
            .send()
            .join();
    }
}
