package com.dershane.userservice.user.application.event;

import com.dershane.userservice.user.domain.Role;
import java.util.UUID;

/**
 * Published in-process while a user is being deactivated, inside the deactivation transaction.
 * Listeners in other modules close what depends on the user (a student's classroom membership). It
 * never leaves the service: it is not an integration event and is not sent to SNS.
 */
public record UserDeactivated(UUID userId, Role role) {}
