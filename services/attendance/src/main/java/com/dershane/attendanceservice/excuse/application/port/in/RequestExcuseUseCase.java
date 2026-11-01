package com.dershane.attendanceservice.excuse.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;

public interface RequestExcuseUseCase {

  /** A guardian reports an excuse for their own child; it starts as {@code PENDING}. */
  ExcuseView request(Actor actor, RequestExcuseCommand command);
}
