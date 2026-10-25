package com.dershane.userservice.user.adapter.out.persistence;

import com.dershane.userservice.user.application.port.in.GuardianView;
import com.dershane.userservice.user.application.port.in.StudentView;
import com.dershane.userservice.user.application.port.in.TeacherView;
import com.dershane.userservice.user.application.port.in.UserView;
import com.dershane.userservice.user.application.port.out.GuardianQueryPort;
import com.dershane.userservice.user.application.port.out.StudentQueryPort;
import com.dershane.userservice.user.application.port.out.TeacherQueryPort;
import com.dershane.userservice.user.application.port.out.UserQueryPort;
import com.dershane.userservice.user.domain.Role;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/** Read side of the user module: views straight from the tables. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class UserQueryAdapter
    implements StudentQueryPort, TeacherQueryPort, GuardianQueryPort, UserQueryPort {

  private final UserJpaRepository users;
  private final StudentJpaRepository students;
  private final TeacherJpaRepository teachers;
  private final GuardianJpaRepository guardians;
  private final UserPersistenceMapper mapper;

  @Override
  public Optional<UserView> findViewById(UUID userId) {
    return users.findById(userId).map(mapper::toUserView);
  }

  @Override
  public Page<UserView> findAdminViews(UUID branchId, Pageable pageable) {
    Pageable sorted = UserSorts.toEntityPaths(pageable);
    Page<UserJpaEntity> page =
        branchId == null
            ? users.findByRole(Role.ADMIN, sorted)
            : users.findByRoleAndBranchId(Role.ADMIN, branchId, sorted);
    return page.map(mapper::toUserView);
  }

  @Override
  public Page<StudentView> findStudentViews(UUID branchId, Boolean active, Pageable pageable) {
    return students
        .findAll(UserSpecifications.inBranch(branchId, active), UserSorts.toEntityPaths(pageable))
        .map(mapper::toStudentView);
  }

  @Override
  public Optional<StudentView> findStudentViewById(UUID studentId) {
    return students.findById(studentId).map(mapper::toStudentView);
  }

  @Override
  public Page<TeacherView> findTeacherViews(UUID branchId, Boolean active, Pageable pageable) {
    return teachers
        .findAll(UserSpecifications.inBranch(branchId, active), UserSorts.toEntityPaths(pageable))
        .map(mapper::toTeacherView);
  }

  @Override
  public Optional<TeacherView> findTeacherViewById(UUID teacherId) {
    return teachers.findById(teacherId).map(mapper::toTeacherView);
  }

  @Override
  public Page<GuardianView> findGuardianViews(UUID branchId, Boolean active, Pageable pageable) {
    return guardians
        .findAll(UserSpecifications.inBranch(branchId, active), UserSorts.toEntityPaths(pageable))
        .map(mapper::toGuardianView);
  }

  @Override
  public Optional<GuardianView> findGuardianViewById(UUID guardianId) {
    return guardians.findById(guardianId).map(mapper::toGuardianView);
  }
}
