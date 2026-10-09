package io.miragon.blueprint.process

import com.ninjasquad.springmockk.MockkBean
import io.camunda.client.CamundaClient
import io.camunda.process.test.api.CamundaAssert
import io.camunda.process.test.api.CamundaProcessTestContext
import io.camunda.process.test.api.CamundaSpringProcessTest
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors
import io.miragon.blueprint.adapter.outbound.zeebe.LeasingProcessAdapter
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase
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
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.bike.BikeUnavailableException
import io.miragon.blueprint.domain.bike.OrderId
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.CustomerName
import io.miragon.blueprint.domain.leasing.Email
import io.miragon.blueprint.domain.leasing.LeasingApplication
import io.miragon.blueprint.domain.leasing.LeasingStatus
import io.miragon.bpmn.runtime.path.ProcessPath
import io.miragon.bpmn.runtime.path.enter
import io.miragon.bpmn.runtime.path.inside
import io.miragon.bpmn.runtime.path.onto
import io.miragon.bpmn.runtime.path.then
import io.miragon.bpmn.runtime.path.throwingCompensation
import io.miragon.common.test.assertions.hasCompletedElements
import io.miragon.common.test.assertions.hasCompletedElementsInOrder
import io.miragon.common.test.config.TestProcessEngineConfiguration
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
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

    @MockkBean(relaxed = true)
    private lateinit var activateLeasingUseCase: ActivateLeasingUseCase

    @BeforeEach
    fun setUp() {
        every { orderBikeUseCase.orderBike(any(), any()) } returns OrderId("ORDER-1")
    }

    @Test
    fun `happy path - contract signed, bike available, leasing becomes active`() {
        val id = submit(age = 35, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        // wait until the contract was sent and the process parks on the await-signature gateway
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract)
        process.correlateContractSigned(id)

        // the fork runs insurance + bike order, then joins and parks on the handover message
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(FlowNodes.ServiceTaskIssueInsurancePolicy, FlowNodes.ServiceTaskOrderBike)
        process.correlateHandoverReported(id)

        // the 14-day withdrawal period elapses
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.EventHandoverReported)
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1))

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElementsInOrder(
                pathUntilContractSigned()
                    .then { it.gatewayFork }
                    .then { it.serviceTaskIssueInsurancePolicy }
                    .then { it.gatewayJoin }
                    .then { it.eventHandoverReported }
                    .then { it.eventWithdrawalPeriodElapsed }
                    .then { it.serviceTaskActivateLeasing }
                    .then { it.endEventLeasingActive },
            )
            .hasCompletedElementsInOrder(
                ProcessPath.from(FlowNodes.GatewayFork)
                    .then { it.gatewayBikeSourceJoin }
                    .then { it.serviceTaskOrderBike }
                    .then { it.gatewayJoin },
            )
            .hasNotActivatedElements(FlowNodes.EventBikeUnavailable.ELEMENT_ID)
        verify(exactly = 1) { sendContractUseCase.sendContract(id) }
        verify(exactly = 1) { issueInsurancePolicyUseCase.issuePolicy(id) }
        verify(exactly = 1) { activateLeasingUseCase.activate(id) }
    }

    @Test
    fun `escalation - contract not signed in time is escalated and rejected`() {
        val id = submit(age = 35, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        // park on the await-signature gateway, then let the 14-day signature deadline elapse
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract)
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1))

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElementsInOrder(pathUntilSignatureAwaited().then { it.eventSignatureDeadline })
            .hasTerminatedElements(FlowNodes.EndEventNotSigned.ELEMENT_ID)
            .hasCompletedElementsInOrder(
                ProcessPath.from(FlowNodes.EventContractNotSigned)
                    .then { it.gatewayRejectionJoin }
                    .then { it.serviceTaskSendRejection }
                    .then { it.endEventApplicationRejected },
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
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElementsInOrder(
            pathUntilCreditRatingChecked()
                .then { it.gatewayRejectionJoin }
                .then { it.serviceTaskSendRejection }
                .then { it.endEventApplicationRejected },
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
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract)
        process.correlateContractSigned(id)
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(FlowNodes.ServiceTaskIssueInsurancePolicy, FlowNodes.ServiceTaskOrderBike)

        // withdraw -> compensation. The cancelBikeOrder call activity runs in its own (child)
        // process instance and parks on its clarify-return task, so we look it up by element id only.
        process.correlateApplicationWithdrawn(id)
        awaitUserTaskCreated(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID)
        processTestContext.completeUserTask(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID)

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(
            ProcessPath.from(FlowNodes.StartEventApplicationWithdrawn)
                .then { it.eventReverseApplication }
                .throwingCompensation(FlowNodes.EventCompensateContract) { it.serviceTaskCancelContract }
                .throwingCompensation(FlowNodes.EventCompensateInsurance) { it.serviceTaskCancelPolicy }
                .throwingCompensation(FlowNodes.EventCompensateOrder) { it.callActivityCancelBikeOrder }
                .then { it.serviceTaskSendCancellationConfirmation }
                .then { it.endEventApplicationCancelled },
        )
        verify(exactly = 1) { cancelContractUseCase.cancelContract(id) }
        verify(exactly = 1) { cancelInsurancePolicyUseCase.cancelPolicy(id) }
        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
    }

    @Test
    fun `bike unavailable - clarifying an alternative re-orders and leasing becomes active`() {
        // the first order finds nothing -> parks on the clarify-alternative user task
        val (id, instanceKey) = submitUntilBikeUnavailable()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)
        acceptAlternative()
        CamundaAssert.assertThatProcessInstance(instance).hasVariable(FlowNodes.UserTaskClarifyAlternative.Variables.BIKE_ID.value, "BIKE-ALT")

        // re-order succeeds -> join -> handover -> withdrawal period
        process.correlateHandoverReported(id)
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.EventHandoverReported)
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1))

        // the first order is left through the error boundary event, so it is terminated, not completed
        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance)
            .hasTerminatedElement(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, 1)
            .hasCompletedElement(FlowNodes.ServiceTaskOrderBike.ELEMENT_ID, 1)
            .hasCompletedElementsInOrder(
                ProcessPath.from(FlowNodes.EventBikeUnavailable)
                    .then { it.userTaskClarifyAlternative }
                    .then { it.gatewayAlternativeFound }
                    .then { it.gatewayBikeSourceJoin }
                    .then { it.serviceTaskOrderBike }
                    .then { it.gatewayJoin }
                    .then { it.eventHandoverReported }
                    .then { it.eventWithdrawalPeriodElapsed }
                    .then { it.serviceTaskActivateLeasing }
                    .then { it.endEventLeasingActive },
            )
        verifyOrder {
            orderBikeUseCase.orderBike(id, BikeId("BIKE-TEST"))
            orderBikeUseCase.orderBike(id, BikeId("BIKE-ALT"))
        }
    }

    @Test
    fun `bike unavailable - declining the alternative reverses contract and policy without cancelling an order`() {
        val (id, instanceKey) = submitUntilBikeUnavailable()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        processTestContext.completeUserTask(
            FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID,
            mapOf(FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.value to false),
        )

        // compensation -> cancellation confirmation -> end cancelled; the failed order registered no compensation
        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(
                ProcessPath.from(FlowNodes.GatewayAlternativeFound)
                    .then { it.eventTriggerReversal }
                    .throwingCompensation(FlowNodes.EventCompensateContract) { it.serviceTaskCancelContract }
                    .throwingCompensation(FlowNodes.EventCompensateInsurance) { it.serviceTaskCancelPolicy }
                    .then { it.serviceTaskConfirmContractCancellation }
                    .then { it.endEventContractCancelled },
            )
            .hasNotActivatedElements(
                FlowNodes.EventCompensateOrder.ELEMENT_ID,
                FlowNodes.CallActivityCancelBikeOrder.ELEMENT_ID,
                FlowNodes.EndEventLeasingActive.ELEMENT_ID,
            )
        assertThat(orderCancellationsOf(instanceKey)).isZero()
        verify(exactly = 1) { cancelContractUseCase.cancelContract(id) }
        verify(exactly = 1) { cancelInsurancePolicyUseCase.cancelPolicy(id) }
        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
        verify(exactly = 0) { requestOrderCancellationUseCase.requestCancellation(any()) }
    }

    @Test
    fun `abort while clarifying an alternative - compensates contract and policy without cancelling an order`() {
        val (id, instanceKey) = submitUntilBikeUnavailable()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        process.correlateApplicationWithdrawn(id)

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(
                ProcessPath.from(FlowNodes.StartEventApplicationWithdrawn)
                    .then { it.eventReverseApplication }
                    .throwingCompensation(FlowNodes.EventCompensateContract) { it.serviceTaskCancelContract }
                    .throwingCompensation(FlowNodes.EventCompensateInsurance) { it.serviceTaskCancelPolicy }
                    .then { it.serviceTaskSendCancellationConfirmation }
                    .then { it.endEventApplicationCancelled },
            )
            .hasNotActivatedElements(
                FlowNodes.EventCompensateOrder.ELEMENT_ID,
                FlowNodes.CallActivityCancelBikeOrder.ELEMENT_ID,
            )
        assertThat(orderCancellationsOf(instanceKey)).isZero()
        verify(exactly = 1) { sendCancellationConfirmationUseCase.sendCancellationConfirmation(id) }
        verify(exactly = 0) { requestOrderCancellationUseCase.requestCancellation(any()) }
    }

    @Test
    fun `abort after an accepted alternative - cancels the one placed order exactly once`() {
        every { requestOrderCancellationUseCase.requestCancellation(any()) } returns true

        val (id, instanceKey) = submitUntilBikeUnavailable()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        // the re-order succeeds -> handover wait state
        acceptAlternative()
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskOrderBike)

        process.correlateApplicationWithdrawn(id)
        awaitUserTaskCreated(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID)
        processTestContext.completeUserTask(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID)

        CamundaAssert.assertThatProcessInstance(instance).isCompleted()
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(FlowNodes.EndEventApplicationCancelled)
            .hasCompletedElement(FlowNodes.CallActivityCancelBikeOrder.ELEMENT_ID, 1)
        assertThat(orderCancellationsOf(instanceKey)).isEqualTo(1)
        verify(exactly = 1) { requestOrderCancellationUseCase.requestCancellation(any()) }
    }

    /** Drives a signed, insured application to the clarify-alternative task: the dealer has no bike. */
    private fun submitUntilBikeUnavailable(): Pair<ApplicationId, Long> {
        every { orderBikeUseCase.orderBike(any(), any()) } throws
            BikeUnavailableException(BikeId("BIKE-TEST")) andThen OrderId("ORDER-2")

        val id = submit(age = 35, income = 3500.0)
        val instanceKey = awaitProcessInstance()
        val instance = ProcessInstanceSelectors.byKey(instanceKey)

        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract)
        process.correlateContractSigned(id)
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(FlowNodes.ServiceTaskIssueInsurancePolicy, FlowNodes.EventBikeUnavailable)
        awaitUserTaskCreated(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instanceKey)
        return id to instanceKey
    }

    private fun acceptAlternative() =
        processTestContext.completeUserTask(
            FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID,
            mapOf(
                FlowNodes.UserTaskClarifyAlternative.Variables.ALTERNATIVE_FOUND.value to true,
                FlowNodes.UserTaskClarifyAlternative.Variables.BIKE_ID.value to "BIKE-ALT",
            ),
        )

    private fun orderCancellationsOf(processInstanceKey: Long): Int =
        camundaClient.newProcessInstanceSearchRequest()
            .filter {
                it.processDefinitionId(CancelBikeOrderProcessApi.PROCESS_ID.value)
                it.parentProcessInstanceKey(processInstanceKey)
            }
            .send()
            .join()
            .items()
            .size

    private fun pathUntilCreditRatingChecked() =
        ProcessPath.from(FlowNodes.StartEventLeasingRequestReceived)
            .then { it.businessRuleTaskCheckCreditRating }
            .then { it.gatewayIsSolvent }

    private fun pathUntilSignatureAwaited() =
        pathUntilCreditRatingChecked()
            .onto { it.subProcessConcludeContract }
            .enter { it.startEventCustomerEligible }
            .then { it.serviceTaskSendContract }
            .then { it.gatewayAwaitSignature }

    private fun pathUntilContractSigned() =
        pathUntilCreditRatingChecked()
            .onto { it.subProcessConcludeContract }
            .inside {
                enter { it.startEventCustomerEligible }
                    .then { it.serviceTaskSendContract }
                    .then { it.gatewayAwaitSignature }
                    .then { it.eventContractSigned }
                    .then { it.endEventContractValid }
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
