package io.miragon.blueprint.adapter.inbound.rest;

import io.miragon.blueprint.application.port.inbound.GetLeasingApplicationQuery;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ContractId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bike-leasing")
public class GetLeasingApplicationController {

    private final GetLeasingApplicationQuery query;

    public GetLeasingApplicationController(GetLeasingApplicationQuery query) {
        this.query = query;
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<LeasingApplicationDto> byId(@PathVariable String applicationId) {
        return query.byId(ApplicationId.of(applicationId))
            .map(result -> ResponseEntity.ok(toDto(result)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record LeasingApplicationDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String applicationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String customerName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bikeId,
        @Schema(types = {"string", "null"}) String bikeModel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        @Schema(types = {"string", "null"}) String orderId,
        @Schema(types = {"string", "null"}) String contractId
    ) {
    }

    private static LeasingApplicationDto toDto(GetLeasingApplicationQuery.Result result) {
        LeasingApplication application = result.application();
        OrderId orderId = application.orderId();
        ContractId contractId = application.contractId();
        return new LeasingApplicationDto(
            application.id().value().toString(),
            application.customerName().value(),
            application.email().value(),
            application.bikeId().value(),
            // resolved from the bike portfolio, not carried on the application
            result.bikeModel(),
            application.status().name(),
            orderId == null ? null : orderId.value(),
            contractId == null ? null : contractId.value());
    }
}
