package io.miragon.blueprint.adapter.outbound.db

import org.springframework.data.jpa.repository.JpaRepository

interface ProcessedJobJpaRepository : JpaRepository<ProcessedJobEntity, Long>
