package com.venuity.server.repo;

import com.venuity.server.domain.IdempotencyKey;
import com.venuity.server.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, IdempotencyKey> {

    long deleteByCreatedAtBefore(Instant cutoff);
}
