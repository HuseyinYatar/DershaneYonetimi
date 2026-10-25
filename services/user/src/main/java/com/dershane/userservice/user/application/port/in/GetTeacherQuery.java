package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;

public interface GetTeacherQuery {

  TeacherView get(Actor actor, UUID teacherId);
}
