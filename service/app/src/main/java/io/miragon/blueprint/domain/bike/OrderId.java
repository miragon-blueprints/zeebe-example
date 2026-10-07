package io.miragon.blueprint.domain.bike;

import static io.miragon.blueprint.domain.DomainPreconditions.requireNotBlank;

public record OrderId(String value) {

    public OrderId {
        requireNotBlank(value, "OrderId must not be blank");
    }
}
