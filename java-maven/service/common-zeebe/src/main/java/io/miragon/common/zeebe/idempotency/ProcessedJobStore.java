package io.miragon.common.zeebe.idempotency;

import java.util.Optional;

public interface ProcessedJobStore {

    Optional<JobOutcome> findOutcome(long jobKey);

    void claim(long jobKey);

    void recordOutcome(long jobKey, JobOutcome outcome);
}
