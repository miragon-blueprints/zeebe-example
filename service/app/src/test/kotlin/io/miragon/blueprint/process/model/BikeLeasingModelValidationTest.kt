package io.miragon.blueprint.process.model

import io.miragon.bpmn.domain.shared.ProcessEngine
import io.miragon.bpmn.testing.BpmnRules
import io.miragon.bpmn.testing.BpmnValidator
import org.junit.jupiter.api.Test

/**
 * Validates the BPMN models themselves (structure, not behaviour) with the `bpmn-to-code-testing`
 * rule engine: all built-in Zeebe rules ([BpmnRules.all]). Runs at build time from the classpath —
 * no engine required.
 */
class BikeLeasingModelValidationTest {

    @Test
    fun `the bpmn models satisfy all rules`() {
        BpmnValidator
            .fromClasspath("bpmn/")
            .engine(ProcessEngine.ZEEBE)
            .withRules(BpmnRules.all())
            .validate()
            .assertNoViolations()
    }
}
