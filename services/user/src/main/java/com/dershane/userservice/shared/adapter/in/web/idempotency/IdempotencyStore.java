package com.dershane.userservice.shared.adapter.in.web.idempotency;

import com.dershane.userservice.shared.application.BusinessCalendar;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence for idempotency keys. Every write runs in its own transaction, independent of the
 * business operation: the key must be claimed before the operation starts and released if it fails.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyStore {

  private final IdempotencyRecordRepository records;
  private final IdempotencyProperties properties;
  private final BusinessCalendar calendar;

  /**
   * Claims the key. Throws {@link org.springframework.dao.DataIntegrityViolationException} if the
   * caller already used it.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public IdempotencyRecord claim(UUID callerId, String key, String requestHash) {
    Instant now = calendar.now();
    return records.saveAndFlush(
        IdempotencyRecord.start(callerId, key, requestHash, now, now.plus(properties.retention())));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
  public Optional<IdempotencyRecord> find(UUID callerId, String key) {
    return records.findByCallerIdAndIdempotencyKey(callerId, key);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void complete(UUID recordId, int httpStatus, String body) {
    records
        .findById(recordId)
        .ifPresentOrElse(
            record -> record.complete(httpStatus, body),
            () ->
                log.atWarn()
                    .addKeyValue("idempotencyRecordId", recordId)
                    .log("Idempotency record vanished before completion; replay won't work"));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void release(UUID recordId) {
    records.deleteById(recordId);
  }

  @Transactional
  @Scheduled(cron = "${user.idempotency.cleanup-cron}")
  public void deleteExpired() {
    int deleted = records.deleteExpired(calendar.now());
    log.atInfo().addKeyValue("deleted", deleted).log("Deleted expired idempotency records");
  }
}
