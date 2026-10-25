package com.dershane.userservice.user.application.port.out;

import com.dershane.userservice.user.application.port.in.GuardianChildView;
import com.dershane.userservice.user.application.port.in.StudentGuardianView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentGuardianQueryPort {

  Optional<StudentGuardianView> findGuardianOfStudent(UUID studentId);

  List<GuardianChildView> findChildren(UUID guardianId);
}
