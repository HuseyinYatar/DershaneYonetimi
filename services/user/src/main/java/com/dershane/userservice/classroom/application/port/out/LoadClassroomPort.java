package com.dershane.userservice.classroom.application.port.out;

import com.dershane.userservice.classroom.domain.Classroom;
import java.util.Optional;
import java.util.UUID;

public interface LoadClassroomPort {

  Optional<Classroom> findById(UUID classroomId);

  boolean existsByBranchIdAndTermAndName(UUID branchId, String term, String name);
}
