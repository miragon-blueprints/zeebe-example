package io.miragon.blueprint.process.model;

import io.miragon.bpmn.domain.shared.ProcessEngine;
import io.miragon.bpmn.testing.BpmnRules;
import io.miragon.bpmn.testing.BpmnValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Validates the BPMN models themselves (structure, not behaviour) with the {@code bpmn-to-code-testing}
 * rule engine: all built-in Zeebe rules ({@link BpmnRules#all}). Runs at build time from the classpath —
 * no engine required.
 */
class BikeLeasingModelValidationTest {

    @Test
    @DisplayName("the bpmn models satisfy all rules")
    void theBpmnModelsSatisfyAllRules() {
        BpmnValidator
            .fromClasspath("bpmn/")
            .engine(ProcessEngine.ZEEBE)
            .withRules(BpmnRules.all())
            .validate()
            .assertNoViolations();
    }
}
