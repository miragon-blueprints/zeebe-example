package io.miragon.blueprint.domain.leasing;

import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.OrderId;
import java.time.LocalDateTime;

/**
 * Aggregate root of the bike-leasing domain. All state transitions return a copy, so an instance is
 * never mutated in place — the calling service persists the returned copy.
 *
 * @param orderId    the placed bike order; {@code null} until the bike was ordered
 * @param contractId the issued contract; {@code null} until the contract system issued one
 */
public record LeasingApplication(
    ApplicationId id,
    CustomerName customerName,
    Email email,
    int age,
    double monthlyNetIncome,
    BikeId bikeId,
    LeasingStatus status,
    LocalDateTime createdAt,
    OrderId orderId,
    ContractId contractId
) {

    public static LeasingApplication receive(
        ApplicationId id,
        CustomerName customerName,
        Email email,
        int age,
        double monthlyNetIncome,
        BikeId bikeId,
        LocalDateTime createdAt
    ) {
        return new LeasingApplication(
            id, customerName, email, age, monthlyNetIncome, bikeId, LeasingStatus.RECEIVED, createdAt, null, null);
    }

    /** Ensures the application is fundamentally processable; throws otherwise. */
    public LeasingApplication validate() {
        if (monthlyNetIncome <= 0.0) {
            throw new ApplicationInvalidException(id, "monthly net income must be greater than zero");
        }
        return this;
    }

    /** Records the contract the contract system issued for this application. */
    public LeasingApplication withContract(ContractId contractId) {
        return new LeasingApplication(
            id, customerName, email, age, monthlyNetIncome, bikeId, status, createdAt, orderId, contractId);
    }

    /** Records the placed order on the application and moves it to ORDERED. */
    public LeasingApplication documentOrder(OrderId orderId) {
        return new LeasingApplication(
            id, customerName, email, age, monthlyNetIncome, bikeId, LeasingStatus.ORDERED, createdAt, orderId, contractId);
    }

    /** Records that the customer accepted a different bike after the requested one was unavailable. */
    public LeasingApplication selectAlternative(BikeId bikeId) {
        return new LeasingApplication(
            id, customerName, email, age, monthlyNetIncome, bikeId, status, createdAt, orderId, contractId);
    }

    /** Records that the bike was handed to the customer, opening the withdrawal period. */
    public LeasingApplication reportHandover() {
        return withStatus(LeasingStatus.HANDED_OVER);
    }

    /** Marks the leasing as live once the withdrawal period elapsed. */
    public LeasingApplication activate() {
        return withStatus(LeasingStatus.ACTIVE);
    }

    /** Marks the application as withdrawn while the asynchronous cancellation runs. */
    public LeasingApplication withdraw() {
        return withStatus(LeasingStatus.WITHDRAWN);
    }

    public LeasingApplication reject() {
        return withStatus(LeasingStatus.REJECTED);
    }

    public LeasingApplication cancel() {
        return withStatus(LeasingStatus.CANCELLED);
    }

    private LeasingApplication withStatus(LeasingStatus status) {
        return new LeasingApplication(
            id, customerName, email, age, monthlyNetIncome, bikeId, status, createdAt, orderId, contractId);
    }
}
