package io.miragon.blueprint.domain.leasing;

import static io.miragon.blueprint.domain.DomainPreconditions.requireNotBlank;

public record CustomerName(String value) {

    public CustomerName {
        requireNotBlank(value, "Customer name must not be blank");
    }
}
