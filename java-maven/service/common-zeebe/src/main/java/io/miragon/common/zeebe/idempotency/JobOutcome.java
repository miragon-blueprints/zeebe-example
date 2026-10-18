package io.miragon.common.zeebe.idempotency;

public sealed interface JobOutcome {

    record Completed(String variablesJson) implements JobOutcome {
    }

    record BpmnErrorThrown(String errorCode, String errorMessage) implements JobOutcome {
    }
}
