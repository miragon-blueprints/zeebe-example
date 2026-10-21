package io.miragon.common.zeebe.config

import io.miragon.common.zeebe.engine.ProcessEngineApi
import io.miragon.common.zeebe.idempotency.IdempotentJobExecutor
import io.miragon.common.zeebe.idempotency.ProcessedJobStore
import io.camunda.client.CamundaClient
import io.camunda.client.api.JsonMapper
import io.camunda.client.annotation.Deployment
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.transaction.support.TransactionOperations

@Configuration
@Deployment(
    resources = [
        "classpath:bpmn/*.bpmn",
        "classpath:dmn/*.dmn",
        "classpath:forms/*.form",
    ],
)
@Import(ZeebeEnvironmentConfiguration::class)
class EngineAutoConfiguration {

    @Bean
    fun processEngineApi(
        camundaClient: CamundaClient,
    ) = ProcessEngineApi(
        camundaClient = camundaClient
    )

    @Bean
    fun idempotentJobExecutor(
        store: ProcessedJobStore,
        transaction: TransactionOperations,
        jsonMapper: JsonMapper,
    ) = IdempotentJobExecutor(
        store = store,
        transaction = transaction,
        jsonMapper = jsonMapper,
    )

}