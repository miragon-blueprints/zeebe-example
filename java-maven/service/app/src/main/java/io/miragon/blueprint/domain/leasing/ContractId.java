package io.miragon.blueprint.domain.leasing;

import static io.miragon.blueprint.domain.DomainPreconditions.requireNotBlank;

/** Reference to the leasing contract issued by the (external) contract system. */
public record ContractId(String value) {

    public ContractId {
        requireNotBlank(value, "ContractId must not be blank");
    }
}
