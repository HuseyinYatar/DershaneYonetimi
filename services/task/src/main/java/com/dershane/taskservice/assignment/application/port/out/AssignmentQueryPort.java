package com.dershane.taskservice.assignment.application.port.out;

import com.dershane.taskservice.assignment.application.port.in.AssignmentDetailView;
import com.dershane.taskservice.assignment.application.port.in.AssignmentSummaryView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AssignmentQueryPort {

  Optional<AssignmentDetailView> findDetailById(UUID assignmentId);

  /** Newest due time first. */
  Page<AssignmentSummaryView> findSummaries(AssignmentFilter filter, Pageable pageable);
}
