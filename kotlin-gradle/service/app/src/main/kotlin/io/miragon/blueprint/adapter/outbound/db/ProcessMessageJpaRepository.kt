package io.miragon.blueprint.adapter.outbound.db

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.QueryHints
import java.util.UUID

interface ProcessMessageJpaRepository : JpaRepository<ProcessMessageEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = SKIP_LOCKED))
    fun findFirstByOrderByCreatedAtAsc(): ProcessMessageEntity?

    companion object {
        const val SKIP_LOCKED = "-2"
    }
}
