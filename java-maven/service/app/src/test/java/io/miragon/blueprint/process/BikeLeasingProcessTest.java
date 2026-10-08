package io.miragon.blueprint.process;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceSelectors;
import io.miragon.blueprint.adapter.outbound.zeebe.LeasingProcessAdapter;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi;
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase;
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase;
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase;
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase;
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase;
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase;
import io.miragon.blueprint.application.port.inbound.SendContractUseCase;
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase;
import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.bpmn.runtime.path.PathWalk;
import io.miragon.common.test.assertions.ProcessPathIds;
import io.miragon.common.test.config.TestProcessEngineConfiguration;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Process test for the MiraVelo Bike-Leasing process on a real (in-container) Camunda 8 engine.
 * {@code @CamundaSpringProcessTest} starts the engine, the workers auto-register from their {@code @JobWorker}
 * annotations, and the use cases are mocked so we assert the <em>process</em> behaviour. Because Zeebe drives
 * itself asynchronously, {@code CamundaAssert} (which polls until a timeout) doubles as the synchronisation
 * mechanism: asserting a wait-state element has completed is how we wait for it. Timers are advanced
 * with {@code processTestContext.increaseTime}, messages are published through the real process adapter, and
 * user tasks are completed through the test context.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@CamundaSpringProcessTest
@ActiveProfiles("test")
@Import(TestProcessEngineConfiguration.class)
class BikeLeasingProcessTest {

    @Autowired
    private CamundaClient camundaClient;

    @Autowired
    private CamundaProcessTestContext processTestContext;

    @Autowired
    private LeasingProcessAdapter process;

    @MockitoBean
    private ValidateApplicationUseCase validateApplicationUseCase;

    @MockitoBean
    private RejectApplicationUseCase rejectApplicationUseCase;

    @MockitoBean
    private SendContractUseCase sendContractUseCase;

    @MockitoBean
    private CancelContractUseCase cancelContractUseCase;

    @MockitoBean
    private IssueInsurancePolicyUseCase issueInsurancePolicyUseCase;

    @MockitoBean
    private CancelInsurancePolicyUseCase cancelInsurancePolicyUseCase;

    @MockitoBean
    private SendSignatureReminderUseCase sendSignatureReminderUseCase;

    @MockitoBean
    private SendCancellationConfirmationUseCase sendCancellationConfirmationUseCase;

    @MockitoBean
    private RequestOrderCancellationUseCase requestOrderCancellationUseCase;

    @MockitoBean
    private BookCancellationCostsUseCase bookCancellationCostsUseCase;

    @MockitoBean
    private OrderBikeUseCase orderBikeUseCase;

    @MockitoBean
    private ActivateLeasingUseCase activateLeasingUseCase;

    @BeforeEach
    void setUp() {
        when(orderBikeUseCase.orderBike(any()))
            .thenReturn(new OrderBikeUseCase.Result(new OrderId("ORDER-1"), true));
    }

    @Test
    @DisplayName("happy path - contract signed, bike available, leasing becomes active")
    void happyPathContractSignedBikeAvailableLeasingBecomesActive() {
        ApplicationId id = submit(35, 3500.0);
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        // wait until the contract was sent and the process parks on the await-signature gateway
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract.ELEMENT_ID);
        process.correlateContractSigned(id);

        // the fork runs insurance + bike order, then joins and parks on the handover message
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, FlowNodes.ServiceTaskOrderBike.ELEMENT_ID);
        process.correlateHandoverReported(id);

        // the 14-day withdrawal period elapses
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.EventHandoverReported.ELEMENT_ID);
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1));

        CamundaAssert.assertThatProcessInstance(instance).isCompleted();
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElementsInOrder(
                ProcessPathIds.inOrder(
                    pathUntilContractSigned()
                        .then(n -> n.gatewayFork())
                        .then(n -> n.serviceTaskIssueInsurancePolicy())
                        .then(n -> n.gatewayJoin())
                        .then(n -> n.eventHandoverReported())
                        .then(n -> n.eventWithdrawalPeriodElapsed())
                        .then(n -> n.serviceTaskActivateLeasing())
                        .end(n -> n.endEventLeasingActive())))
            .hasCompletedElementsInOrder(
                ProcessPathIds.inOrder(
                    PathWalk.from(FlowNodes.GatewayFork.INSTANCE)
                        .then(n -> n.gatewayBikeSourceJoin())
                        .then(n -> n.serviceTaskOrderBike())
                        .then(n -> n.gatewayBikeAvailable())
                        .then(n -> n.gatewayJoin())));
        verify(sendContractUseCase, times(1)).sendContract(id);
        verify(issueInsurancePolicyUseCase, times(1)).issuePolicy(id);
        verify(activateLeasingUseCase, times(1)).activate(id);
    }

    @Test
    @DisplayName("escalation - contract not signed in time is escalated and rejected")
    void escalationContractNotSignedInTimeIsEscalatedAndRejected() {
        ApplicationId id = submit(35, 3500.0);
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        // park on the await-signature gateway, then let the 14-day signature deadline elapse
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract.ELEMENT_ID);
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1));

        CamundaAssert.assertThatProcessInstance(instance).isCompleted();
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElementsInOrder(ProcessPathIds.inOrder(pathUntilSignatureAwaited().then(n -> n.eventSignatureDeadline())))
            .hasTerminatedElements(FlowNodes.EndEventNotSigned.ELEMENT_ID)
            .hasCompletedElementsInOrder(
                ProcessPathIds.inOrder(
                    PathWalk.from(FlowNodes.EventContractNotSigned.INSTANCE)
                        .then(n -> n.gatewayRejectionJoin())
                        .then(n -> n.serviceTaskSendRejection())
                        .end(n -> n.endEventApplicationRejected())));
        verify(rejectApplicationUseCase, times(1)).reject(id);
    }

    @Test
    @DisplayName("not solvent - the DMN routes the application straight to rejection")
    void notSolventTheDmnRoutesTheApplicationStraightToRejection() {
        // age below 18 cannot sign a leasing contract, so the DMN returns solvent = false
        ApplicationId id = submit(15, 3500.0);
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        CamundaAssert.assertThatProcessInstance(instance).isCompleted();
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElementsInOrder(
            ProcessPathIds.inOrder(
                pathUntilCreditRatingChecked()
                    .then(n -> n.gatewayRejectionJoin())
                    .then(n -> n.serviceTaskSendRejection())
                    .end(n -> n.endEventApplicationRejected())));
        verify(rejectApplicationUseCase, times(1)).reject(id);
        verify(sendContractUseCase, never()).sendContract(any());
    }

    @Test
    @DisplayName("abort - withdrawing the application compensates the completed steps")
    void abortWithdrawingTheApplicationCompensatesTheCompletedSteps() {
        when(requestOrderCancellationUseCase.requestCancellation(any())).thenReturn(true);

        ApplicationId id = submit(35, 3500.0);
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        // drive to the handover wait state (contract signed, bike ordered, insured)
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract.ELEMENT_ID);
        process.correlateContractSigned(id);
        CamundaAssert.assertThatProcessInstance(instance)
            .hasCompletedElements(FlowNodes.ServiceTaskIssueInsurancePolicy.ELEMENT_ID, FlowNodes.ServiceTaskOrderBike.ELEMENT_ID);

        // withdraw -> compensation. The cancelBikeOrder call activity runs in its own (child)
        // process instance and parks on its clarify-return task, so we look it up by element id only.
        process.correlateApplicationWithdrawn(id);
        awaitUserTaskCreated(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID);
        processTestContext.completeUserTask(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID);

        CamundaAssert.assertThatProcessInstance(instance).isCompleted();
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(
            ProcessPathIds.distinct(
                PathWalk.from(FlowNodes.StartEventApplicationWithdrawn.INSTANCE)
                    .then(n -> n.eventReverseApplication())
                    .throwingCompensation(FlowNodes.EventCompensateContract.INSTANCE, n -> n.serviceTaskCancelContract())
                    .throwingCompensation(FlowNodes.EventCompensateInsurance.INSTANCE, n -> n.serviceTaskCancelPolicy())
                    .throwingCompensation(FlowNodes.EventCompensateOrder.INSTANCE, n -> n.callActivityCancelBikeOrder())
                    .then(n -> n.serviceTaskSendCancellationConfirmation())
                    .end(n -> n.endEventApplicationCancelled())));
        verify(cancelContractUseCase, times(1)).cancelContract(id);
        verify(cancelInsurancePolicyUseCase, times(1)).cancelPolicy(id);
        verify(sendCancellationConfirmationUseCase, times(1)).sendCancellationConfirmation(id);
    }

    @Test
    @DisplayName("bike unavailable - clarifying an alternative re-orders and leasing becomes active")
    void bikeUnavailableClarifyingAnAlternativeReOrdersAndLeasingBecomesActive() {
        // the first order finds the requested bike unavailable; the re-order after the alternative succeeds
        when(orderBikeUseCase.orderBike(any()))
            .thenReturn(
                new OrderBikeUseCase.Result(null, false),
                new OrderBikeUseCase.Result(new OrderId("ORDER-2"), true));

        ApplicationId id = submit(35, 3500.0);
        long instanceKey = awaitProcessInstance();
        ProcessInstanceSelector instance = ProcessInstanceSelectors.byKey(instanceKey);

        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.ServiceTaskSendContract.ELEMENT_ID);
        process.correlateContractSigned(id);

        // the first order finds nothing -> parks on the clarify-alternative user task
        awaitUserTaskCreated(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID, instanceKey);
        processTestContext.completeUserTask(
            FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID,
            Map.of("alternativeFound", true, "bikeId", "BIKE-ALT"));

        // re-order succeeds -> join -> handover -> withdrawal period
        process.correlateHandoverReported(id);
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElements(FlowNodes.EventHandoverReported.ELEMENT_ID);
        processTestContext.increaseTime(Duration.ofDays(14).plusHours(1));

        CamundaAssert.assertThatProcessInstance(instance).isCompleted();
        CamundaAssert.assertThatProcessInstance(instance).hasCompletedElementsInOrder(
            ProcessPathIds.inOrder(
                PathWalk.from(FlowNodes.GatewayFork.INSTANCE)
                    .then(n -> n.gatewayBikeSourceJoin())
                    .then(n -> n.serviceTaskOrderBike())
                    .then(n -> n.gatewayBikeAvailable())
                    .then(n -> n.userTaskClarifyAlternative())
                    .then(n -> n.gatewayAlternativeFound())
                    .then(n -> n.gatewayBikeSourceJoin())
                    .then(n -> n.serviceTaskOrderBike())
                    .then(n -> n.gatewayBikeAvailable())
                    .then(n -> n.gatewayJoin())
                    .then(n -> n.eventHandoverReported())
                    .then(n -> n.eventWithdrawalPeriodElapsed())
                    .then(n -> n.serviceTaskActivateLeasing())
                    .end(n -> n.endEventLeasingActive())));
        verify(orderBikeUseCase, times(2)).orderBike(id);
    }

    private PathWalk<FlowNodes.GatewayIsSolvent, FlowNodes.GatewayIsSolvent.Next> pathUntilCreditRatingChecked() {
        return PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
            .then(n -> n.serviceTaskValidateApplication())
            .then(n -> n.businessRuleTaskCheckCreditRating())
            .then(n -> n.gatewayIsSolvent());
    }

    private PathWalk<FlowNodes.GatewayAwaitSignature, FlowNodes.GatewayAwaitSignature.Next> pathUntilSignatureAwaited() {
        return pathUntilCreditRatingChecked()
            .onto(n -> n.subProcessConcludeContract())
            .enter(FlowNodes.SubProcessConcludeContract.INSTANCE, s -> s.startEventCustomerEligible())
            .then(n -> n.serviceTaskSendContract())
            .then(n -> n.gatewayAwaitSignature());
    }

    private PathWalk<FlowNodes.SubProcessConcludeContract, FlowNodes.SubProcessConcludeContract.Next> pathUntilContractSigned() {
        return pathUntilCreditRatingChecked()
            .onto(n -> n.subProcessConcludeContract())
            .inside(FlowNodes.SubProcessConcludeContract.INSTANCE, s ->
                PathWalk.from(s.startEventCustomerEligible())
                    .then(n -> n.serviceTaskSendContract())
                    .then(n -> n.gatewayAwaitSignature())
                    .then(n -> n.eventContractSigned())
                    .end(n -> n.endEventContractValid()));
    }

    private ApplicationId submit(int age, double income) {
        LeasingApplication application =
            LeasingApplication.receive(
                ApplicationId.newId(),
                new CustomerName("Test Customer"),
                new Email("test@example.com"),
                age,
                income,
                new BikeId("BIKE-TEST"),
                LocalDateTime.now());
        process.submitRequest(application);
        return application.id();
    }

    private long awaitProcessInstance() {
        AtomicLong instanceKey = new AtomicLong(0L);
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted(() -> {
                List<ProcessInstance> instances = camundaClient.newProcessInstanceSearchRequest()
                    .filter(filter -> filter.processDefinitionId(BikeLeasingProcessProcessApi.PROCESS_ID.getValue()))
                    .send()
                    .join()
                    .items();
                assert !instances.isEmpty() : "no bike-leasing process instance was started";
                instanceKey.set(instances.getFirst().getProcessInstanceKey());
            });
        return instanceKey.get();
    }

    private void awaitUserTaskCreated(String elementId) {
        awaitUserTaskCreated(elementId, null);
    }

    private void awaitUserTaskCreated(String elementId, Long processInstanceKey) {
        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(200))
            .untilAsserted(() -> {
                List<UserTask> tasks = camundaClient.newUserTaskSearchRequest()
                    .filter(filter -> {
                        filter.elementId(elementId);
                        if (processInstanceKey != null) {
                            filter.processInstanceKey(processInstanceKey);
                        }
                    })
                    .send()
                    .join()
                    .items();
                assert !tasks.isEmpty() : "user task '" + elementId + "' was not created";
            });
    }
}
