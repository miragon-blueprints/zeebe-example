package io.miragon.blueprint.adapter.inbound.zeebe

import io.camunda.client.annotation.JobWorker
import io.camunda.client.annotation.Variable
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Variables
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.ServiceTasks
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.springframework.stereotype.Component

@Component
class OrderBikeWorker(
    private val useCase: OrderBikeUseCase,
) {

    @JobWorker(type = ServiceTasks.MIRAVELO_ORDER_BIKE)
    fun handle(@Variable applicationId: String): Map<String, Any?> {
        val result = useCase.orderBike(ApplicationId.of(applicationId))
        return mapOf(
            Variables.ServiceTaskOrderBike.ORDER_ID.value to result.orderId?.value,
            Variables.ServiceTaskOrderBike.BIKE_AVAILABLE.value to result.bikeAvailable,
        )
    }
}
