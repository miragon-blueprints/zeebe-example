package io.miragon.blueprint.adapter.inbound.zeebe;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.miragon.blueprint.adapter.process.ServiceTasks;
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.common.zeebe.idempotency.IdempotentJobExecutor;
import org.springframework.stereotype.Component;

@Component
public class BookCostsWorker {

    private final BookCancellationCostsUseCase useCase;

    private final IdempotentJobExecutor idempotentJobs;

    public BookCostsWorker(BookCancellationCostsUseCase useCase, IdempotentJobExecutor idempotentJobs) {
        this.useCase = useCase;
        this.idempotentJobs = idempotentJobs;
    }

    @JobWorker(type = ServiceTasks.MIRAVELO_BOOK_COSTS)
    public void handle(ActivatedJob job, @Variable String orderId) {
        idempotentJobs.runOnce(job, () -> useCase.bookCosts(new OrderId(orderId)));
    }
}
