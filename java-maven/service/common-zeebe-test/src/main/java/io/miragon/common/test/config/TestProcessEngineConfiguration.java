package io.miragon.common.test.config;

import io.camunda.client.CamundaClient;
import io.miragon.common.zeebe.engine.ProcessEngineApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test configuration that ensures ProcessEngineApi uses the test CamundaClient
 * provided by @CamundaProcessTest instead of the production client.
 */
@TestConfiguration
public class TestProcessEngineConfiguration {

    @Bean
    @Primary
    @ConditionalOnBean(CamundaClient.class)
    public ProcessEngineApi testProcessEngineApi(CamundaClient camundaClient) {
        return new ProcessEngineApi(camundaClient);
    }
}
