package com.dershane.userservice.user.application.port.out;

import com.dershane.userservice.user.application.port.in.TeacherView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TeacherQueryPort {

  Optional<TeacherView> findTeacherViewById(UUID teacherId);

  /** Null filters are not applied. */
  Page<TeacherView> findTeacherViews(UUID branchId, Boolean active, Pageable pageable);
}
