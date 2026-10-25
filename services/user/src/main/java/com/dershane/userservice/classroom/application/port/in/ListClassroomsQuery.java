package com.dershane.userservice.classroom.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListClassroomsQuery {

  /** The branch defaults to the caller's branch; null filters are not applied. */
  Page<ClassroomView> list(
      Actor actor, UUID branchId, String term, Boolean active, Pageable pageable);
}
