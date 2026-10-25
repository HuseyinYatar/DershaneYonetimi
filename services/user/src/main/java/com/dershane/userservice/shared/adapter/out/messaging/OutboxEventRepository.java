package com.dershane.userservice.shared.adapter.out.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  /**
   * Locks the oldest unpublished events. SKIP LOCKED lets several pods publish in parallel without
   * sending the same event twice.
   */
  @Query(
      value =
          """
          select * from outbox_event
          where published_at is null
          order by created_at
          limit :batchSize
          for update skip locked
          """,
      nativeQuery = true)
  List<OutboxEvent> lockNextBatch(int batchSize);

  long countByPublishedAtIsNull();

  @Modifying
  @Query("delete from OutboxEvent e where e.publishedAt is not null and e.publishedAt < :before")
  int deletePublishedBefore(Instant before);
}
