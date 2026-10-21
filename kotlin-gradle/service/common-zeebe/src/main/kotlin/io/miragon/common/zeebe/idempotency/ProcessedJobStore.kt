package io.miragon.common.zeebe.idempotency

interface ProcessedJobStore {

    fun findOutcome(jobKey: Long): JobOutcome?

    fun claim(jobKey: Long)

    fun recordOutcome(jobKey: Long, outcome: JobOutcome)
}
