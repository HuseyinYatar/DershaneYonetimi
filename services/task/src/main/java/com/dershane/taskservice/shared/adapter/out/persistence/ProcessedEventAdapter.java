package com.dershane.taskservice.shared.adapter.out.persistence;

import com.dershane.taskservice.shared.application.port.out.ProcessedEventPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The unique key makes two pods that receive the same duplicate wait for each other: the second
 * insert sees the committed row and does nothing.
 *
 * <p>shortcut: rows are never deleted (an id is a few bytes); add a retention job past the queue's
 * 14-day maximum retention if the table ever grows large.
 */
@Component
@RequiredArgsConstructor
class ProcessedEventAdapter implements ProcessedEventPort {

  private final JdbcClient jdbc;

  @Override
  public boolean markProcessed(UUID eventId, String eventType) {
    return jdbc.sql(
                "insert into processed_event (event_id, event_type) values (:id, :type)"
                    + " on conflict (event_id) do nothing")
            .param("id", eventId)
            .param("type", eventType)
            .update()
        == 1;
  }
}
