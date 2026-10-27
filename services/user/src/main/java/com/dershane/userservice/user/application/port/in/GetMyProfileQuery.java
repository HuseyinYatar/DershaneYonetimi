package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;

public interface GetMyProfileQuery {

  UserView me(Actor actor);
}
