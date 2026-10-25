package com.dershane.userservice.user.application.service;

import com.dershane.userservice.branch.application.port.in.LoadBranchQuery;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.application.BranchAccessPolicy;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.subject.application.port.in.LoadSubjectQuery;
import com.dershane.userservice.user.application.port.in.GetTeacherQuery;
import com.dershane.userservice.user.application.port.in.ListTeachersQuery;
import com.dershane.userservice.user.application.port.in.RegisterTeacherCommand;
import com.dershane.userservice.user.application.port.in.RegisterTeacherUseCase;
import com.dershane.userservice.user.application.port.in.RegistrationResult;
import com.dershane.userservice.user.application.port.in.TeacherView;
import com.dershane.userservice.user.application.port.in.UpdateTeacherCommand;
import com.dershane.userservice.user.application.port.in.UpdateTeacherUseCase;
import com.dershane.userservice.user.application.port.out.TeacherQueryPort;
import com.dershane.userservice.user.domain.PersonalInfo;
import com.dershane.userservice.user.domain.Role;
import com.dershane.userservice.user.domain.Teacher;
import com.dershane.userservice.user.domain.UsernamePolicy;
import io.micrometer.observation.annotation.Observed;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class TeacherService
    implements RegisterTeacherUseCase, UpdateTeacherUseCase, GetTeacherQuery, ListTeachersQuery {

  private static final String CREATE = "teacher.create";
  private static final String UPDATE = "teacher.update";

  private final TeacherQueryPort teacherQuery;
  private final UserLookup userLookup;
  private final LoadBranchQuery branchLookup;
  private final LoadSubjectQuery subjectLookup;
  private final UserAccountService accountService;
  private final UsernamePolicy usernamePolicy;
  private final BranchAccessPolicy branchAccess;
  private final UserAccessPolicy userAccess;

  @Override
  @Observed(name = CREATE)
  public RegistrationResult<TeacherView> register(Actor actor, RegisterTeacherCommand command) {
    branchAccess.requireBranchAdmin(actor, command.branchId(), CREATE);
    branchLookup.requireActive(command.branchId(), CREATE);
    subjectLookup.requireActive(command.specialtySubjectId(), CREATE);
    PersonalInfo info =
        new PersonalInfo(command.firstName(), command.lastName(), command.email(), command.phone());
    String username = usernamePolicy.forStaff(info, Role.TEACHER.name());
    AccountCreated<Teacher> created =
        accountService.create(
            new NewAccount(Role.TEACHER, command.branchId(), info, username),
            id ->
                Teacher.register(
                    id, command.branchId(), info, username, command.specialtySubjectId()));
    return new RegistrationResult<>(
        requireView(created.user().getId()),
        created.passwordSetupEmailSent(),
        created.temporaryPassword());
  }

  @Override
  @Observed(name = UPDATE)
  public TeacherView update(Actor actor, UUID teacherId, UpdateTeacherCommand command) {
    Teacher teacher = userLookup.requireTeacher(teacherId);
    userAccess.requireCanManage(actor, teacher, UPDATE);
    teacher.requireActive(UPDATE);
    if (command.specialtySubjectId() != null) {
      subjectLookup.requireActive(command.specialtySubjectId(), UPDATE);
    }
    PersonalInfo newInfo =
        teacher
            .personalInfo()
            .merge(
                new PersonalInfo(
                    command.firstName(), command.lastName(), command.email(), command.phone()));
    Teacher saved =
        accountService.update(
            teacher,
            newInfo,
            t -> {
              if (command.specialtySubjectId() != null) {
                t.changeSpecialty(command.specialtySubjectId());
              }
            });
    return requireView(saved.getId());
  }

  @Override
  @Transactional(readOnly = true)
  public TeacherView get(Actor actor, UUID teacherId) {
    Teacher teacher = userLookup.requireTeacher(teacherId);
    userAccess.requireCanView(actor, teacher);
    return requireView(teacherId);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<TeacherView> list(Actor actor, UUID branchId, Boolean active, Pageable pageable) {
    UUID branchFilter = branchAccess.resolveBranchFilter(actor, branchId, "teacher.list");
    return teacherQuery.findTeacherViews(branchFilter, active, pageable);
  }

  private TeacherView requireView(UUID teacherId) {
    return teacherQuery
        .findTeacherViewById(teacherId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.TEACHER_NOT_FOUND, "Teacher not found", "teacherId", teacherId));
  }
}
