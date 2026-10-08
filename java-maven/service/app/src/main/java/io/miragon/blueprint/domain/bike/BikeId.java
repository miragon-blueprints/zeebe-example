package io.miragon.blueprint.domain.bike;

import static io.miragon.blueprint.domain.DomainPreconditions.requireNotBlank;

/** Identifies the concrete bike a leasing application is about — carried through to the order. */
public record BikeId(String value) {

    public BikeId {
        requireNotBlank(value, "BikeId must not be blank");
    }
}
