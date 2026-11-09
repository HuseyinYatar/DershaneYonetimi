package com.dershane.messagingservice.report.application.port.out;

import com.dershane.messagingservice.report.domain.MessageReport;
import com.dershane.messagingservice.report.domain.ReportStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportPort {

  Optional<MessageReport> findById(UUID reportId);

  boolean existsByMessageAndReporter(UUID messageId, UUID reporterId);

  /** Newest first; {@code branchId} and {@code status} may be null for "any". */
  Page<MessageReport> findAll(UUID branchId, ReportStatus status, Pageable pageable);

  MessageReport save(MessageReport report);
}
