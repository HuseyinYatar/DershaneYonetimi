package com.dershane.userservice.classroom.application.port.out;

import com.dershane.userservice.classroom.application.port.in.ClassroomView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClassroomQueryPort {

  Optional<ClassroomView> findViewById(UUID classroomId);

  /** Null filters are not applied. */
  Page<ClassroomView> findViews(UUID branchId, String term, Boolean active, Pageable pageable);
}
