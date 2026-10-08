package io.miragon.blueprint.adapter.inbound.rest;

import io.miragon.blueprint.application.port.inbound.ListBikesQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/bikes} — the bike catalogue for the submit form's picker. Exists so nobody has to type a
 * bike id by hand; unavailable bikes (e.g. {@code BIKE-OOS}) stay in the list on purpose, so a user can
 * drive the bike-unavailable scenario from the UI.
 */
@RestController
@RequestMapping("/api/bikes")
public class ListBikesController {

    private final ListBikesQuery query;

    public ListBikesController(ListBikesQuery query) {
        this.query = query;
    }

    @Operation(operationId = "listBikes")
    @GetMapping
    public List<BikeDto> all() {
        return query.all().stream()
            .map(bike -> new BikeDto(bike.bikeId().value(), bike.model(), bike.available()))
            .toList();
    }

    public record BikeDto(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String bikeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String model,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean available
    ) {
    }
}
