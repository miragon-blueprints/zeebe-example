package io.miragon.blueprint.domain.leasing;

import static io.miragon.blueprint.domain.DomainPreconditions.requireNonNull;

import java.util.UUID;

public record ApplicationId(UUID value) {

    public ApplicationId {
        requireNonNull(value, "ApplicationId must not be null");
    }

    public static ApplicationId newId() {
        return new ApplicationId(UUID.randomUUID());
    }

    public static ApplicationId of(String value) {
        return new ApplicationId(UUID.fromString(value));
    }
}
