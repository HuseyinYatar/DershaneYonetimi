package com.dershane.userservice.user.adapter.out.persistence;

import com.dershane.userservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.user.application.port.out.LoadStudentPort;
import com.dershane.userservice.user.application.port.out.LoadUserPort;
import com.dershane.userservice.user.application.port.out.SaveUserPort;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.User;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class UserPersistenceAdapter implements LoadUserPort, LoadStudentPort, SaveUserPort {

  private final UserJpaRepository users;
  private final StudentJpaRepository students;
  private final UserPersistenceMapper mapper;

  @Override
  public Optional<User> findById(UUID userId) {
    return users.findById(userId).map(mapper::toDomain);
  }

  @Override
  public boolean existsByEmail(String email) {
    return users.existsByInfoEmail(email);
  }

  @Override
  public boolean existsByEmailAndIdNot(String email, UUID userId) {
    return users.existsByInfoEmailAndIdNot(email, userId);
  }

  @Override
  public boolean existsByUsername(String username) {
    return users.existsByUsername(username);
  }

  @Override
  public Optional<Student> findByIdForUpdate(UUID studentId) {
    return students.findByIdForUpdate(studentId).map(entity -> (Student) mapper.toDomain(entity));
  }

  @Override
  public boolean existsStudentNumber(UUID branchId, String studentNumber) {
    return students.existsStudentNumber(branchId, studentNumber);
  }

  @Override
  public boolean existsStudentNumberForOther(UUID branchId, String studentNumber, UUID studentId) {
    return students.existsStudentNumberForOther(branchId, studentNumber, studentId);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T extends User> T save(T user) {
    UserJpaEntity entity;
    if (user.getVersion() == null) {
      entity = users.saveAndFlush(mapper.toEntity(user));
    } else {
      entity =
          users
              .findById(user.getId())
              .orElseThrow(
                  () ->
                      Rejections.reject(
                          log,
                          ErrorCode.USER_NOT_FOUND,
                          "User to save no longer exists",
                          "userId",
                          user.getId()));
      VersionChecks.requireCurrent(
          log, "User", user.getId(), user.getVersion(), entity.getVersion());
      mapper.update(entity, user);
      users.flush();
    }
    return (T) mapper.toDomain(entity);
  }
}
