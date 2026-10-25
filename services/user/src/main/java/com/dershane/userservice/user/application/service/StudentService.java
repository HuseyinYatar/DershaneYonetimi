package com.dershane.userservice.user.application.service;

import com.dershane.userservice.branch.application.port.in.LoadBranchQuery;
import com.dershane.userservice.branch.domain.Branch;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.shared.application.BranchAccessPolicy;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import com.dershane.userservice.user.application.port.in.GetStudentQuery;
import com.dershane.userservice.user.application.port.in.ListStudentsQuery;
import com.dershane.userservice.user.application.port.in.RegisterStudentCommand;
import com.dershane.userservice.user.application.port.in.RegisterStudentUseCase;
import com.dershane.userservice.user.application.port.in.RegistrationResult;
import com.dershane.userservice.user.application.port.in.StudentView;
import com.dershane.userservice.user.application.port.in.UpdateStudentCommand;
import com.dershane.userservice.user.application.port.in.UpdateStudentUseCase;
import com.dershane.userservice.user.application.port.out.LoadStudentPort;
import com.dershane.userservice.user.application.port.out.StudentQueryPort;
import com.dershane.userservice.user.application.port.out.StudentRelationsPort;
import com.dershane.userservice.user.domain.PersonalInfo;
import com.dershane.userservice.user.domain.Role;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.UsernamePolicy;
import io.micrometer.observation.annotation.Observed;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student profiles. Not transactional at the class level: account creation calls Keycloak outside
 * the database transaction (see {@link UserAccountService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
class StudentService
    implements RegisterStudentUseCase, UpdateStudentUseCase, GetStudentQuery, ListStudentsQuery {

  private static final String CREATE = "student.create";
  private static final String UPDATE = "student.update";

  private final LoadStudentPort loadStudent;
  private final StudentQueryPort studentQuery;
  private final StudentRelationsPort studentRelations;
  private final UserLookup userLookup;
  private final LoadBranchQuery branchLookup;
  private final UserAccountService accountService;
  private final UsernamePolicy usernamePolicy;
  private final BranchAccessPolicy branchAccess;
  private final UserAccessPolicy userAccess;

  @Override
  @Observed(name = CREATE)
  public RegistrationResult<StudentView> register(Actor actor, RegisterStudentCommand command) {
    branchAccess.requireBranchAdmin(actor, command.branchId(), CREATE);
    Branch branch = branchLookup.requireActive(command.branchId(), CREATE);
    String studentNumber = command.studentNumber().strip();
    if (loadStudent.existsStudentNumber(branch.getId(), studentNumber)) {
      throw Rejections.reject(
          log,
          ErrorCode.STUDENT_NUMBER_TAKEN,
          "Student creation rejected: student number already used in branch",
          "branchId",
          branch.getId());
    }
    PersonalInfo info =
        new PersonalInfo(command.firstName(), command.lastName(), command.email(), command.phone());
    String username = usernamePolicy.forStudent(info, branch.getCode(), studentNumber);
    AccountCreated<Student> created =
        accountService.create(
            new NewAccount(Role.STUDENT, branch.getId(), info, username),
            id ->
                Student.register(
                    id,
                    branch.getId(),
                    info,
                    username,
                    studentNumber,
                    command.gradeLevel(),
                    command.targetExam()));
    return new RegistrationResult<>(
        requireView(created.user().getId()),
        created.passwordSetupEmailSent(),
        created.temporaryPassword());
  }

  @Override
  @Observed(name = UPDATE)
  public StudentView update(Actor actor, UUID studentId, UpdateStudentCommand command) {
    Student student = userLookup.requireStudent(studentId);
    userAccess.requireCanManage(actor, student, UPDATE);
    student.requireActive(UPDATE);
    String newNumber = command.studentNumber() == null ? null : command.studentNumber().strip();
    if (newNumber != null
        && loadStudent.existsStudentNumberForOther(student.getBranchId(), newNumber, studentId)) {
      throw Rejections.reject(
          log,
          ErrorCode.STUDENT_NUMBER_TAKEN,
          "Student update rejected: student number already used in branch",
          "studentId",
          studentId);
    }
    requireGradeChangeAllowed(student, command.gradeLevel());
    PersonalInfo newInfo =
        student
            .personalInfo()
            .merge(
                new PersonalInfo(
                    command.firstName(), command.lastName(), command.email(), command.phone()));
    Student saved =
        accountService.update(
            student,
            newInfo,
            s -> s.updateStudentInfo(newNumber, command.gradeLevel(), command.targetExam()));
    return requireView(saved.getId());
  }

  @Override
  @Transactional(readOnly = true)
  public StudentView get(Actor actor, UUID studentId) {
    Student student = userLookup.requireStudent(studentId);
    userAccess.requireCanView(actor, student);
    return requireView(studentId);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<StudentView> list(Actor actor, UUID branchId, Boolean active, Pageable pageable) {
    UUID branchFilter = branchAccess.resolveBranchFilter(actor, branchId, "student.list");
    return studentQuery.findStudentViews(branchFilter, active, pageable);
  }

  /**
   * An enrolled student's grade must match the classroom, so the grade can only change after the
   * student is removed from the classroom (e.g. at the end of the term).
   */
  private void requireGradeChangeAllowed(Student student, Integer newGrade) {
    boolean gradeChanges = newGrade != null && newGrade != student.getGradeLevel();
    if (gradeChanges && studentRelations.hasActiveClassroom(student.getId())) {
      throw Rejections.reject(
          log,
          ErrorCode.GRADE_LEVEL_MISMATCH,
          "Grade change rejected: student is enrolled in a classroom of the current grade",
          "studentId",
          student.getId());
    }
  }

  private StudentView requireView(UUID studentId) {
    return studentQuery
        .findStudentViewById(studentId)
        .orElseThrow(
            () ->
                Rejections.reject(
                    log, ErrorCode.STUDENT_NOT_FOUND, "Student not found", "studentId", studentId));
  }
}
