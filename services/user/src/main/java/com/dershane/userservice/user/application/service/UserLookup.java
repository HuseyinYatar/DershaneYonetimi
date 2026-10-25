package com.dershane.userservice.user.application.service;

import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.user.application.port.in.LoadUserQuery;
import com.dershane.userservice.user.application.port.out.LoadStudentPort;
import com.dershane.userservice.user.application.port.out.LoadUserPort;
import com.dershane.userservice.user.domain.Guardian;
import com.dershane.userservice.user.domain.Role;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.Teacher;
import com.dershane.userservice.user.domain.User;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Loads users by role and rejects (with a log line) when missing or of another role. */
@Slf4j
@Component
@RequiredArgsConstructor
class UserLookup implements LoadUserQuery {

  private final LoadUserPort users;
  private final LoadStudentPort students;

  @Transactional(readOnly = true)
  User require(UUID userId) {
    return users
        .findById(userId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.USER_NOT_FOUND, "User not found", "userId", userId));
  }

  @Override
  @Transactional(readOnly = true)
  public Student requireStudent(UUID userId) {
    return requireRole(userId, Student.class, ErrorCode.STUDENT_NOT_FOUND);
  }

  @Override
  @Transactional(readOnly = true)
  public Teacher requireTeacher(UUID userId) {
    return requireRole(userId, Teacher.class, ErrorCode.TEACHER_NOT_FOUND);
  }

  @Transactional(readOnly = true)
  Guardian requireGuardian(UUID userId) {
    return requireRole(userId, Guardian.class, ErrorCode.GUARDIAN_NOT_FOUND);
  }

  @Transactional(readOnly = true)
  User requireAdmin(UUID userId) {
    User user = require(userId);
    if (user.getRole() != Role.ADMIN) {
      throw Rejections.reject(
          log,
          ErrorCode.ADMIN_NOT_FOUND,
          "User is not an admin",
          "userId",
          userId,
          "role",
          user.getRole());
    }
    return user;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Student lockStudent(UUID userId) {
    return students
        .findByIdForUpdate(userId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.STUDENT_NOT_FOUND, "Student not found", "studentId", userId));
  }

  private <T extends User> T requireRole(UUID userId, Class<T> type, ErrorCode notFound) {
    User user =
        users
            .findById(userId)
            .orElseThrow(
                () -> Rejections.reject(log, notFound, "User not found", "userId", userId));
    if (!type.isInstance(user)) {
      throw Rejections.reject(
          log,
          notFound,
          "User exists but has another role",
          "userId",
          userId,
          "expected",
          type.getSimpleName(),
          "actual",
          user.getRole());
    }
    return type.cast(user);
  }
}
