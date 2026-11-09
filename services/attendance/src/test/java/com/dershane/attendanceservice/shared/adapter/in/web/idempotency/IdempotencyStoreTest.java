package com.dershane.attendanceservice.shared.adapter.in.web.idempotency;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.dershane.attendanceservice.shared.application.BusinessCalendar;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdempotencyStoreTest {

  private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");

  @Mock private IdempotencyRecordRepository records;
  @Mock private BusinessCalendar calendar;

  @Test
  void anExpiredRecordOfTheSameKeyIsRemovedBeforeTheKeyIsClaimedAgain() {
    UUID caller = UUID.randomUUID();
    when(calendar.now()).thenReturn(NOW);
    when(records.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    IdempotencyStore store =
        new IdempotencyStore(records, new IdempotencyProperties(Duration.ofHours(48)), calendar);

    store.claim(caller, "key-1", "hash");

    InOrder order = inOrder(records);
    order.verify(records).deleteExpiredOf(caller, "key-1", NOW);
    order.verify(records).saveAndFlush(any());
  }
}
