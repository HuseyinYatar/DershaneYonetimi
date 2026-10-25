package com.dershane.userservice.shared.adapter.in.web.idempotency;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

  Optional<IdempotencyRecord> findByCallerIdAndIdempotencyKey(UUID callerId, String key);

  @Modifying
  @Query("delete from IdempotencyRecord r where r.expiresAt < :now")
  int deleteExpired(Instant now);
}
