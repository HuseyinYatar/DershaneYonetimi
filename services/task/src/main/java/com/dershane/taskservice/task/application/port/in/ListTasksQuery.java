package com.dershane.taskservice.task.application.port.in;

import com.dershane.taskservice.shared.application.Actor;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListTasksQuery {

  /**
   * Teachers list their own tasks; admins list their branch (super admins any branch). Null filters
   * are not applied.
   */
  Page<TaskView> list(
      Actor actor, UUID teacherId, UUID branchId, Boolean active, Pageable pageable);
}
