package io.miragon.blueprint.adapter.outbound.db;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

public interface ProcessMessageJpaRepository extends JpaRepository<ProcessMessageEntity, UUID> {

    String SKIP_LOCKED = "-2";

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
    Optional<ProcessMessageEntity> findFirstByOrderByCreatedAtAsc();
}
