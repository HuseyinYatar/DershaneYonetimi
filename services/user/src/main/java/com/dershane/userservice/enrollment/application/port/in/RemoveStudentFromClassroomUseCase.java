package com.dershane.userservice.enrollment.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface RemoveStudentFromClassroomUseCase {

  MembershipView remove(Actor actor, UUID studentId);
}
