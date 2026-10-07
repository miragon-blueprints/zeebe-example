package io.miragon.blueprint.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.miragon.blueprint.application.port.inbound.ListLeasingApplicationsQuery;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/bike-leasing?status=&page=&size=} — the customer-portal list. A separate controller from
 * the paging query keeps to the "one inbound port per controller" rule the architecture tests enforce.
 */
@RestController
@RequestMapping("/api/bike-leasing")
public class ListLeasingApplicationsController {

    private final ListLeasingApplicationsQuery query;

    public ListLeasingApplicationsController(ListLeasingApplicationsQuery query) {
        this.query = query;
    }

    @Operation(operationId = "listLeasingApplications")
    @GetMapping
    public LeasingApplicationPageDto list(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        ListLeasingApplicationsQuery.Filter filter = new ListLeasingApplicationsQuery.Filter(
            status == null ? null : parseStatus(status),
            page,
            size);
        return toDto(query.list(filter));
    }

    private static LeasingStatus parseStatus(String raw) {
        try {
            return LeasingStatus.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            String expected = Arrays.stream(LeasingStatus.values()).map(String::valueOf).collect(Collectors.joining(", "));
            throw new IllegalArgumentException("unknown status '" + raw + "'; expected one of " + expected, e);
        }
    }

    private static LeasingApplicationPageDto toDto(ListLeasingApplicationsQuery.Page page) {
        return new LeasingApplicationPageDto(
            page.items().stream().map(ListLeasingApplicationsController::toDto).toList(),
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages());
    }

    private static LeasingApplicationSummaryDto toDto(ListLeasingApplicationsQuery.Item item) {
        return new LeasingApplicationSummaryDto(
            item.applicationId().value().toString(),
            item.customerName().value(),
            item.bikeId().value(),
            item.bikeModel(),
            item.status().name(),
            item.createdAt());
    }

    public record LeasingApplicationPageDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<LeasingApplicationSummaryDto> items,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int size,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages
    ) {
    }

    public record LeasingApplicationSummaryDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String applicationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String customerName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bikeId,
        @Schema(types = {"string", "null"}) String bikeModel,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        // Serialised as an ISO-8601 string so the JSON carries a stable, machine-parseable timestamp.
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        LocalDateTime createdAt
    ) {
    }
}
