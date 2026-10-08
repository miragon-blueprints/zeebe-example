package io.miragon.blueprint.domain.leasing;

import io.miragon.blueprint.domain.bike.BikeId;
import java.time.LocalDateTime;

/**
 * A leasing application parked on the {@code Clarify alternative with customer} user task because the
 * requested bike was unavailable — the read model behind the back-office inbox. It carries what an
 * agent needs to pick up the case (who, which bike, since when), but deliberately no engine task id:
 * the case is resolved through the domain, correlated by application id.
 *
 * @param requestedBikeModel the bike's model from the portfolio; {@code null} if the bike is unknown there
 */
public record PendingClarification(
    ApplicationId applicationId,
    CustomerName customerName,
    BikeId requestedBikeId,
    String requestedBikeModel,
    LocalDateTime waitingSince
) {
}
