package io.miragon.blueprint.process

import com.ninjasquad.springmockk.MockkBean
import io.camunda.client.CamundaClient
import io.camunda.process.test.api.CamundaAssert
import io.camunda.process.test.api.CamundaProcessTestContext
import io.camunda.process.test.api.CamundaSpringProcessTest
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors
import io.miragon.blueprint.adapter.outbound.zeebe.LeasingProcessAdapter
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase
import io.miragon.blueprint.application.port.inbound.SendContractUseCase
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.CustomerName
import io.miragon.blueprint.domain.leasing.Email
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.common.test.assertions.hasCompletedElements
import io.miragon.common.test.config.TestProcessEngineConfiguration
import io.mockk.every
import io.mockk.verify
import org.awaitility.Awaitility
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Duration
import java.time.LocalDateTime

/**
 * Process test for the MiraVelo Bike-Leasing process on a real (in-container) Camunda 8 engine.
 * `@CamundaSpringProcessTest` starts the engine, the workers auto-register from their `@JobWorker`
 * annotations, and the use cases are mocked so we assert the *process* behaviour. Because Zeebe drives
 * itself asynchronously, `CamundaAssert` (which polls until a timeout) doubles as the synchronisation
 * mechanism: asserting a wait-state element has completed is how we wait for it. Timers are advanced
 * with `processTestContext.increaseTime`, messages are published through the real process adapter, and
 * user tasks are completed through the test context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@CamundaSpringProcessTest
@ActiveProfiles("test")
@Import(TestProcessEngineConfiguration::class)
class BikeLeasingProcessTest {

    @Autowired
    private lateinit var camundaClient: CamundaClient

    @Autowired
    private lateinit var processTestContext: CamundaProcessTestContext

    @Autowired
    private lateinit var process: LeasingProcessAdapter

    @MockkBean(relaxed = true)
    private lateinit var validateApplicationUseCase: ValidateApplicationUseCase

    @MockkBean(relaxed = true)
    private lateinit var rejectApplicationUseCase: RejectApplicationUseCase

    @MockkBean(relaxed = true)
    private lateinit var sendContractUseCase: SendContractUseCase

    @MockkBean(relaxed = true)
    private lateinit var cancelContractUseCase: CancelContractUseCase

    @MockkBean(relaxed = true)
    private lateinit var issueInsurancePolicyUseCase: IssueInsurancePolicyUseCase

    @MockkBean(relaxed = true)
    private lateinit var cancelInsurancePolicyUseCase: CancelInsurancePolicyUseCase

    @MockkBean(relaxed = true)
    private lateinit var sendSignatureReminderUseCase: SendSignatureReminderUseCase

    @MockkBean(relaxed = true)
    private lateinit var sendCancellationConfirmationUseCase: SendCancellationConfirmationUseCase

    @MockkBean(relaxed = true)
    private lateinit var requestOrderCancellationUseCase: RequestOrderCancellationUseCase

    @MockkBean(relaxed = true)
    private lateinit var bookCancellationCostsUseCase: BookCancellationCostsUseCase

    @MockkBean(relaxed = true)
    private lateinit var orderBikeUseCase: OrderBikeUseCase

    @BeforeEach
    fun setUp() {
        every { orderBikeUseCase.orderBike(any()) } returns
            OrderBikeUseCase.Result(OrderId("ORDER-1"), bikeAvailable = true)
    }

    @Test
    fun `happy path - contract signed, bike available, leasing becomes active`() {
        val id = submit(age = 35, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        // wait until the contract was sent and the process parks on the await-signature gateway
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(Elements.SERVICE_TASK_SEND_CONTRACT)
        process.correlateContractSigned(id)

        // the fork runs insurance + bike order, then joins and parks on the handover message
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(Elements.SERVICE_TASK_ISSUE_INSURANCE_POLICY, Elements.SERVICE_TASK_ORDER_BIKE)
        process.correlateHandoverReported(id)

        // the 14-day withdrawal period elapses
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(Elements.EVENT_HANDOVER_REPORTED)
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1))

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(
            Elements.SERVICE_TASK_VALIDATE_APPLICATION,
            Elements.BUSINESS_RULE_TASK_CHECK_CREDIT_RATING,
            Elements.SERVICE_TASK_SEND_CONTRACT,
            Elements.SERVICE_TASK_ISSUE_INSURANCE_POLICY,
            Elements.SERVICE_TASK_ORDER_BIKE,
            Elements.EVENT_HANDOVER_REPORTED,
            Elements.END_EVENT_LEASING_ACTIVE,
        )
        verify(exactly = 1) { sendContractUseCase.sendContract(id) }
        verify(exactly = 1) { issueInsurancePolicyUseCase.issuePolicy(id) }
    }

    @Test
    fun `escalation - contract not signed in time is escalated and rejected`() {
        val id = submit(age = 35, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        // park on the await-signature gateway, then let the 14-day signature deadline elapse
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(Elements.SERVICE_TASK_SEND_CONTRACT)
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1))

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(
            Elements.EVENT_SIGNATURE_DEADLINE,
            Elements.BOUNDARY_CONTRACT_NOT_SIGNED,
            Elements.SERVICE_TASK_SEND_REJECTION,
            Elements.END_EVENT_APPLICATION_REJECTED,
        )
        verify(exactly = 1) { rejectApplicationUseCase.reject(id) }
    }

    @Test
    fun `not solvent - the DMN routes the application straight to rejection`() {
        // age below 18 cannot sign a leasing contract, so the DMN returns solvent = false
        val id = submit(age = 15, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(
            Elements.SERVICE_TASK_VALIDATE_APPLICATION,
            Elements.BUSINESS_RULE_TASK_CHECK_CREDIT_RATING,
            Elements.SERVICE_TASK_SEND_REJECTION,
            Elements.END_EVENT_APPLICATION_REJECTED,
        )
        verify(exactly = 1) { rejectApplicationUseCase.reject(id) }
        verify(exactly = 0) { sendContractUseCase.sendContract(any()) }
    }

    @Test
    fun `abort - withdrawing the application compensates the completed steps`() {
        every { requestOrderCancellationUseCase.requestCancellation(any()) } returns true

        val id = submit(age = 35, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        // drive to the handover wait state (contract signed, bike ordered, insured)
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(Elements.SERVICE_TASK_SEND_CONTRACT)
        process.correlateContractSigned(id)
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(Elements.SERVICE_TASK_ISSUE_INSURANCE_POLICY, Elements.SERVICE_TASK_ORDER_BIKE)

        // withdraw -> compensation. The cancelBikeOrder call activity runs in its own (child)
        // process instance and parks on its clarify-return task, so we look it up by element id only.
        process.correlateApplicationWithdrawn(id)
        awaitUserTaskCreated(CancelBikeOrderProcessApi.Elements.USER_TASK_CLARIFY_RETURN.value)
        processTestContext.completeUserTask(CancelBikeOrderProcessApi.Elements.USER_TASK_CLARIFY_RETURN.value)

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(
            Elements.SERVICE_TASK_CANCEL_CONTRACT,
            Elements.SERVICE_TASK_CANCEL_POLICY,
            Elements.CALL_ACTIVITY_CANCEL_BIKE_ORDER,
            Elements.SERVICE_TASK_SEND_CANCELLATION_CONFIRMATION,
            Elements.END_EVENT_APPLICATION_CANCELLED,
        )
        verify(exactly = 1) { cancelContractUseCase.cancelContract(id) }
        verify(exactly = 1) { cancelInsurancePolicyUseCase.cancelPolicy(id) }
        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
    }

    @Test
    fun `bike unavailable - clarifying an alternative re-orders and leasing becomes active`() {
        // the first order finds the requested bike unavailable; the re-order after the alternative succeeds
        every { orderBikeUseCase.orderBike(any()) } returnsMany
            listOf(
                OrderBikeUseCase.Result(orderId = null, bikeAvailable = false),
                OrderBikeUseCase.Result(OrderId("ORDER-2"), bikeAvailable = true),
            )

        val id = submit(age = 35, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(Elements.SERVICE_TASK_SEND_CONTRACT)
        process.correlateContractSigned(id)

        // the first order finds nothing -> parks on the clarify-alternative user task
        awaitUserTaskCreated(Elements.USER_TASK_CLARIFY_ALTERNATIVE.value, instanceKey)
        processTestContext.completeUserTask(
            Elements.USER_TASK_CLARIFY_ALTERNATIVE.value,
            mapOf("alternativeFound" to true, "bikeId" to "BIKE-ALT"),
        )

        // re-order succeeds -> join -> handover -> withdrawal period
        process.correlateHandoverReported(id)
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(Elements.EVENT_HANDOVER_REPORTED)
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1))

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(
            Elements.USER_TASK_CLARIFY_ALTERNATIVE,
            Elements.SERVICE_TASK_ORDER_BIKE,
            Elements.END_EVENT_LEASING_ACTIVE,
        )
        verify(exactly = 2) { orderBikeUseCase.orderBike(id) }
    }

    private fun submit(age: Int, income: Double, bikeId: String = "BIKE-TEST"): ApplicationId {
        val application =
            LeasingApplication(
                id = ApplicationId.new(),
                customerName = CustomerName("Test Customer"),
                email = Email("test@example.com"),
                age = age,
                monthlyNetIncome = income,
                bikeId = BikeId(bikeId),
                status = LeasingStatus.RECEIVED,
                createdAt = LocalDateTime.now(),
            )
        process.submitRequest(application)
        return application.id
    }

    private fun awaitProcessInstance(): Long {
        var instanceKey = 0L
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted {
                val instances = camundaClient.newProcessInstanceSearchRequest()
                    .filter { it.processDefinitionId(BikeLeasingProcessProcessApi.PROCESS_ID.value) }
                    .send()
                    .join()
                    .items()
                assert(instances.isNotEmpty()) { "no bike-leasing process instance was started" }
                instanceKey = instances.first().processInstanceKey
            }
        return instanceKey
    }

    private fun awaitUserTaskCreated(elementId: String, processInstanceKey: Long? = null) {
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted {
                val tasks = camundaClient.newUserTaskSearchRequest()
                    .filter {
                        it.elementId(elementId)
                        if (processInstanceKey != null) it.processInstanceKey(processInstanceKey)
                    }
                    .send()
                    .join()
                    .items()
                assert(tasks.isNotEmpty()) { "user task '$elementId' was not created" }
            }
    }
}
