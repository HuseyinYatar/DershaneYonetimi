package com.dershane.userservice.user.application.port.out;

import com.dershane.userservice.user.application.port.in.StudentView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StudentQueryPort {

  Optional<StudentView> findStudentViewById(UUID studentId);

  /** Null filters are not applied. */
  Page<StudentView> findStudentViews(UUID branchId, Boolean active, Pageable pageable);
}
