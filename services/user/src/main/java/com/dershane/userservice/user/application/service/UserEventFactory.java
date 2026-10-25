package com.dershane.userservice.user.application.service;

import com.dershane.userservice.shared.application.event.IntegrationEvent.UserUpdated;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.User;
import org.springframework.stereotype.Component;

@Component
class UserEventFactory {

  /**
   * Must be called with a user returned by {@code SaveUserPort.save}, so {@code version} is the
   * committed record version consumers use to order updates.
   */
  UserUpdated userUpdated(User user) {
    String studentNumber = user instanceof Student student ? student.getStudentNumber() : null;
    return new UserUpdated(
        user.getId(),
        user.getRole().name(),
        user.getBranchId(),
        user.getInfo().firstName(),
        user.getInfo().lastName(),
        user.getInfo().email(),
        user.getInfo().phone(),
        user.isActive(),
        studentNumber,
        user.getVersion());
  }
}
