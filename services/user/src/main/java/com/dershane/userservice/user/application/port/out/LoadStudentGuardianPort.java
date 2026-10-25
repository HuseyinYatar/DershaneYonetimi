package com.dershane.userservice.user.application.port.out;

import com.dershane.userservice.user.domain.StudentGuardian;
import java.util.Optional;
import java.util.UUID;

public interface LoadStudentGuardianPort {

  Optional<StudentGuardian> findByStudentId(UUID studentId);

  boolean existsByGuardianIdAndStudentId(UUID guardianId, UUID studentId);
}
