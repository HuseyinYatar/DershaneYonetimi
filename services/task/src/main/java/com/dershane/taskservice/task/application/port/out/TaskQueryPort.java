package com.dershane.taskservice.task.application.port.out;

import com.dershane.taskservice.task.application.port.in.TaskView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TaskQueryPort {

  Optional<TaskView> findViewById(UUID taskId);

  /** Null filters are not applied. */
  Page<TaskView> findViews(UUID branchId, UUID teacherId, Boolean active, Pageable pageable);
}
