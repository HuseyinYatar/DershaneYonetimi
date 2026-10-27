package com.dershane.userservice.enrollment.adapter.in.event;

import com.dershane.userservice.enrollment.application.port.in.CloseActiveMembershipUseCase;
import com.dershane.userservice.user.application.event.UserDeactivated;
import com.dershane.userservice.user.domain.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Closes a deactivated student's classroom membership. The event is published inside the
 * deactivation transaction and handled synchronously, so both changes commit or roll back together.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class UserDeactivatedListener {

  private static final String ACTION = "user.deactivate";

  private final CloseActiveMembershipUseCase closeActiveMembership;

  @EventListener
  void onUserDeactivated(UserDeactivated event) {
    if (event.role() != Role.STUDENT) {
      log.atDebug()
          .addKeyValue("userId", event.userId())
          .addKeyValue("role", event.role())
          .log("Deactivated user is not a student; no classroom membership to close");
      return;
    }
    closeActiveMembership.closeActiveMembership(event.userId(), ACTION);
  }
}
