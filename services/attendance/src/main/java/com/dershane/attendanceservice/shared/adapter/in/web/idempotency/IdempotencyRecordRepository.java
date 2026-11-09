package com.dershane.attendanceservice.shared.adapter.in.web.idempotency;

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

  /**
   * Removes an expired record of this caller and key that the scheduled cleanup has not deleted
   * yet, so the key can be claimed again and the old response is never replayed after its retention
   * ran out.
   */
  @Modifying
  @Query(
      "delete from IdempotencyRecord r where r.callerId = :callerId"
          + " and r.idempotencyKey = :key and r.expiresAt < :now")
  int deleteExpiredOf(UUID callerId, String key, Instant now);
}
