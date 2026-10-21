package io.miragon.common.zeebe.idempotency

sealed interface JobOutcome {

    data class Completed(val variablesJson: String) : JobOutcome

    data class BpmnErrorThrown(val errorCode: String, val errorMessage: String?) : JobOutcome
}
