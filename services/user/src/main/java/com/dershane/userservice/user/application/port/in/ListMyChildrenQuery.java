package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.List;

public interface ListMyChildrenQuery {

  /** Only for guardians. */
  List<GuardianChildView> myChildren(Actor actor);
}
