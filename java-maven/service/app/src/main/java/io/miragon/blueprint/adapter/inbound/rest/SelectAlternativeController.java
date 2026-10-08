package io.miragon.blueprint.adapter.inbound.rest;

import io.miragon.blueprint.application.port.inbound.SelectAlternativeUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * External completion of the {@code Clarify alternative with customer} user task — the counterpart to a
 * human completing the deployed Camunda Form in the Camunda Tasklist.
 */
@RestController
@RequestMapping("/api/bike-leasing")
public class SelectAlternativeController {

    private final SelectAlternativeUseCase useCase;

    public SelectAlternativeController(SelectAlternativeUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/{applicationId}/clarify-alternative")
    public ResponseEntity<Void> clarifyAlternative(
        @PathVariable String applicationId,
        @RequestBody AlternativeDecisionInput input
    ) {
        useCase.selectAlternative(
            new SelectAlternativeUseCase.Command(
                ApplicationId.of(applicationId),
                input.alternativeFound(),
                input.bikeId() == null ? null : new BikeId(input.bikeId()),
                input.bikeModel()));
        return ResponseEntity.accepted().build();
    }

    public record AlternativeDecisionInput(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean alternativeFound,
        @Schema(types = {"string", "null"}) String bikeId,
        @Schema(types = {"string", "null"}) String bikeModel
    ) {
    }
}
