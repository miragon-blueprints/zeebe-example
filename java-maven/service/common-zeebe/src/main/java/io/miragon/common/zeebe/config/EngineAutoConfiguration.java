package io.miragon.common.zeebe.config;

import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.Deployment;
import io.camunda.client.api.JsonMapper;
import io.miragon.common.zeebe.engine.ProcessEngineApi;
import io.miragon.common.zeebe.idempotency.IdempotentJobExecutor;
import io.miragon.common.zeebe.idempotency.ProcessedJobStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionOperations;

@Configuration
@Deployment(
    resources = {
        "classpath:bpmn/*.bpmn",
        "classpath:dmn/*.dmn",
        "classpath:forms/*.form",
    }
)
@Import(ZeebeEnvironmentConfiguration.class)
public class EngineAutoConfiguration {

    @Bean
    public ProcessEngineApi processEngineApi(CamundaClient camundaClient) {
        return new ProcessEngineApi(camundaClient);
    }

    @Bean
    public IdempotentJobExecutor idempotentJobExecutor(
        ProcessedJobStore store,
        TransactionOperations transaction,
        JsonMapper jsonMapper
    ) {
        return new IdempotentJobExecutor(store, transaction, jsonMapper);
    }
}
