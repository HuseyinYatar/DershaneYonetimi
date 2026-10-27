package com.dershane.userservice.user.application.port.out;

import com.dershane.userservice.user.domain.Student;
import java.util.Optional;
import java.util.UUID;

public interface LoadStudentPort {

  /** Locks the row until the transaction ends; serializes classroom and guardian changes. */
  Optional<Student> findByIdForUpdate(UUID studentId);

  boolean existsStudentNumber(UUID branchId, String studentNumber);

  boolean existsStudentNumberForOther(UUID branchId, String studentNumber, UUID studentId);
}
