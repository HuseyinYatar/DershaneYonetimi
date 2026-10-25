package com.dershane.userservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.dershane.userservice.enrollment.adapter.in.web.dto.ClassroomStudentResponse;
import com.dershane.userservice.enrollment.adapter.in.web.dto.ClassroomTeacherResponse;
import com.dershane.userservice.enrollment.application.port.in.ClassroomStudentView;
import com.dershane.userservice.enrollment.application.port.in.ClassroomTeacherView;
import com.dershane.userservice.shared.adapter.out.messaging.EventEnvelope;
import com.dershane.userservice.shared.application.event.IntegrationEvent.StudentAddedToClassroom;
import com.dershane.userservice.shared.application.event.IntegrationEvent.UserUpdated;
import com.dershane.userservice.user.adapter.in.web.dto.CreateGuardianRequest;
import com.dershane.userservice.user.adapter.in.web.dto.CreateStudentRequest;
import com.dershane.userservice.user.adapter.in.web.dto.GuardianChildResponse;
import com.dershane.userservice.user.adapter.in.web.dto.StudentGuardianResponse;
import com.dershane.userservice.user.adapter.in.web.dto.StudentResponse;
import com.dershane.userservice.user.adapter.in.web.dto.TemporaryPasswordResponse;
import com.dershane.userservice.user.adapter.in.web.dto.UpdateStudentRequest;
import com.dershane.userservice.user.adapter.in.web.dto.UserCreatedResponse;
import com.dershane.userservice.user.application.port.in.GuardianChildView;
import com.dershane.userservice.user.application.port.in.RegisterGuardianCommand;
import com.dershane.userservice.user.application.port.in.RegisterStudentCommand;
import com.dershane.userservice.user.application.port.in.RegistrationResult;
import com.dershane.userservice.user.application.port.in.StudentGuardianView;
import com.dershane.userservice.user.application.port.in.StudentView;
import com.dershane.userservice.user.application.port.in.TemporaryPasswordResult;
import com.dershane.userservice.user.application.port.in.UpdateStudentCommand;
import com.dershane.userservice.user.application.port.out.IdentityProfile;
import com.dershane.userservice.user.application.port.out.NewIdentity;
import com.dershane.userservice.user.domain.PersonalInfo;
import com.dershane.userservice.user.domain.Relationship;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.TargetExam;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * KVKK: objects carrying personal data may end up in a log line through string concatenation or an
 * exception message. Their {@code toString} must not reveal names, contact details, student numbers
 * or passwords.
 */
class PersonalDataMaskingTest {

  private static final String FIRST = "Zeynep";
  private static final String LAST = "Öztürk";
  private static final String EMAIL = "zeynep@example.com";
  private static final String PHONE = "+905551112233";
  private static final String NUMBER = "998877";
  private static final String PASSWORD = "Secret12345x";
  private static final List<String> SECRETS = List.of(FIRST, LAST, EMAIL, PHONE, NUMBER, PASSWORD);

  private final UUID id = UUID.randomUUID();
  private final UUID branchId = UUID.randomUUID();
  private final PersonalInfo info = new PersonalInfo(FIRST, LAST, EMAIL, PHONE);

  @Test
  void webRequestsAreMasked() {
    assertMasked(
        new CreateStudentRequest(branchId, FIRST, LAST, EMAIL, PHONE, NUMBER, 12, TargetExam.YKS));
    assertMasked(new UpdateStudentRequest(FIRST, LAST, EMAIL, PHONE, NUMBER, 12, TargetExam.YKS));
    assertMasked(new CreateGuardianRequest(branchId, FIRST, LAST, EMAIL, PHONE));
  }

  @Test
  void commandsAreMasked() {
    assertMasked(
        new RegisterStudentCommand(
            branchId, FIRST, LAST, EMAIL, PHONE, NUMBER, 12, TargetExam.YKS));
    assertMasked(new UpdateStudentCommand(FIRST, LAST, EMAIL, PHONE, NUMBER, 12, TargetExam.YKS));
    assertMasked(new RegisterGuardianCommand(branchId, FIRST, LAST, EMAIL, PHONE));
  }

  @Test
  void webResponsesAreMasked() {
    StudentResponse student =
        new StudentResponse(
            id,
            branchId,
            FIRST,
            LAST,
            EMAIL,
            PHONE,
            EMAIL,
            true,
            NUMBER,
            12,
            TargetExam.YKS,
            Instant.now(),
            Instant.now());
    assertMasked(student);
    assertMasked(new UserCreatedResponse<>(student, false, PASSWORD, false));
    assertMasked(new TemporaryPasswordResponse(PASSWORD));
    assertMasked(
        new StudentGuardianResponse(id, id, FIRST, LAST, EMAIL, PHONE, Relationship.MOTHER));
    assertMasked(new GuardianChildResponse(id, FIRST, LAST, NUMBER, 12, Relationship.MOTHER, true));
    assertMasked(new ClassroomStudentResponse(id, FIRST, LAST, NUMBER, id, LocalDate.now()));
    assertMasked(new ClassroomTeacherResponse(id, id, FIRST, LAST, id, "Matematik"));
  }

  @Test
  void viewsAndResultsAreMasked() {
    StudentView student =
        new StudentView(
            id,
            branchId,
            FIRST,
            LAST,
            EMAIL,
            PHONE,
            EMAIL,
            true,
            NUMBER,
            12,
            TargetExam.YKS,
            Instant.now(),
            Instant.now());
    assertMasked(student);
    assertMasked(new RegistrationResult<>(student, false, PASSWORD));
    assertMasked(new TemporaryPasswordResult(PASSWORD));
    assertMasked(new StudentGuardianView(id, id, FIRST, LAST, EMAIL, PHONE, Relationship.MOTHER));
    assertMasked(new GuardianChildView(id, FIRST, LAST, NUMBER, 12, Relationship.MOTHER, true));
    assertMasked(new ClassroomStudentView(id, FIRST, LAST, NUMBER, id, LocalDate.now()));
    assertMasked(new ClassroomTeacherView(id, id, FIRST, LAST, id, "Matematik"));
  }

  @Test
  void identityAndValueObjectsAreMasked() {
    assertMasked(info);
    assertMasked(new NewIdentity(EMAIL, EMAIL, FIRST, LAST, "student", branchId, PASSWORD));
    assertMasked(new IdentityProfile(EMAIL, FIRST, LAST));
  }

  @Test
  void eventsAndEnvelopesAreMasked() {
    UserUpdated updated =
        new UserUpdated(id, "STUDENT", branchId, FIRST, LAST, EMAIL, PHONE, true, NUMBER, 3);
    assertMasked(updated);
    assertMasked(
        new StudentAddedToClassroom(
            id, id, id, branchId, NUMBER, FIRST + " " + LAST, LocalDate.now()));
    assertMasked(new EventEnvelope(id, "UserUpdated", 1, Instant.now(), "user", updated));
  }

  @Test
  void domainObjectsPrintOnlyTheirId() {
    Student student = Student.register(id, branchId, info, EMAIL, NUMBER, 12, TargetExam.YKS);

    assertMasked(student);
    assertThat(student.toString()).contains(id.toString());
  }

  private static void assertMasked(Object value) {
    String text = value.toString();
    for (String secret : SECRETS) {
      assertThat(text).as("%s leaks personal data", value.getClass()).doesNotContain(secret);
    }
  }
}
