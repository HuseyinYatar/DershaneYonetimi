package com.dershane.userservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dershane.userservice.branch.application.port.out.SaveBranchPort;
import com.dershane.userservice.branch.domain.Branch;
import com.dershane.userservice.classroom.application.port.out.SaveClassroomPort;
import com.dershane.userservice.classroom.domain.Classroom;
import com.dershane.userservice.enrollment.application.port.out.LoadMembershipPort;
import com.dershane.userservice.enrollment.application.port.out.SaveMembershipPort;
import com.dershane.userservice.enrollment.domain.ClassroomMembership;
import com.dershane.userservice.shared.adapter.out.messaging.OutboxEvent;
import com.dershane.userservice.shared.adapter.out.messaging.OutboxEventRepository;
import com.dershane.userservice.support.TestContainers;
import com.dershane.userservice.user.application.port.out.SaveStudentGuardianPort;
import com.dershane.userservice.user.application.port.out.SaveUserPort;
import com.dershane.userservice.user.domain.Guardian;
import com.dershane.userservice.user.domain.PersonalInfo;
import com.dershane.userservice.user.domain.Relationship;
import com.dershane.userservice.user.domain.Student;
import com.dershane.userservice.user.domain.StudentGuardian;
import com.dershane.userservice.user.domain.TargetExam;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Business rules the database enforces as a last line of defence against concurrent requests. Runs
 * the real Flyway migration on PostgreSQL; {@code ddl-auto=validate} also checks that the entity
 * mapping matches the schema. It goes through the persistence adapters, so the domain/entity
 * mapping is exercised too.
 */
@DataJpaTest(
    includeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.dershane\\.userservice\\..*\\.adapter\\.out\\.persistence\\..*"))
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryConstraintsIT {

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> TestContainers.postgres().getJdbcUrl());
    registry.add("spring.datasource.username", () -> TestContainers.postgres().getUsername());
    registry.add("spring.datasource.password", () -> TestContainers.postgres().getPassword());
  }

  @Autowired private SaveBranchPort branches;
  @Autowired private SaveUserPort users;
  @Autowired private SaveClassroomPort classrooms;
  @Autowired private SaveMembershipPort memberships;
  @Autowired private LoadMembershipPort loadMemberships;
  @Autowired private SaveStudentGuardianPort studentGuardians;
  @Autowired private OutboxEventRepository outbox;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  private Branch branch;
  private Classroom classroomA;
  private Classroom classroomB;

  @BeforeEach
  void setUp() {
    branch = newBranch();
    classroomA = classrooms.save(Classroom.create(branch.getId(), "12-A", 12, "2026-2027"));
    classroomB = classrooms.save(Classroom.create(branch.getId(), "12-B", 12, "2026-2027"));
  }

  @Test
  void studentCannotHaveTwoActiveClassrooms() {
    Student student = newStudent(branch, "1001");
    memberships.saveAndFlush(
        ClassroomMembership.start(student.getId(), classroomA.getId(), LocalDate.now()));

    assertViolates(
        () ->
            memberships.saveAndFlush(
                ClassroomMembership.start(student.getId(), classroomB.getId(), LocalDate.now())),
        "uk_membership_active_student");
  }

  @Test
  void closingAndFlushingTheOldMembershipFirstAllowsTheMove() {
    Student student = newStudent(branch, "1002");
    ClassroomMembership old =
        memberships.saveAndFlush(
            ClassroomMembership.start(student.getId(), classroomA.getId(), LocalDate.now()));

    old.close(LocalDate.now());
    memberships.saveAndFlush(old);
    memberships.saveAndFlush(
        ClassroomMembership.start(student.getId(), classroomB.getId(), LocalDate.now()));

    assertThat(loadMemberships.findActiveByStudentId(student.getId()))
        .get()
        .extracting(ClassroomMembership::getClassroomId)
        .isEqualTo(classroomB.getId());
  }

  @Test
  void staleMembershipCannotOverwriteANewerOne() {
    Student student = newStudent(branch, "1004");
    ClassroomMembership saved =
        memberships.saveAndFlush(
            ClassroomMembership.start(student.getId(), classroomA.getId(), LocalDate.now()));
    ClassroomMembership first =
        loadMemberships.findActiveByStudentId(student.getId()).orElseThrow();
    ClassroomMembership second =
        loadMemberships.findActiveByStudentId(student.getId()).orElseThrow();

    first.close(LocalDate.now());
    memberships.saveAndFlush(first);
    entityManager.clear();
    second.close(LocalDate.now());

    assertThat(saved.getVersion()).isZero();
    assertThatThrownBy(() -> memberships.saveAndFlush(second))
        .isInstanceOf(com.dershane.userservice.shared.domain.DomainException.class)
        .extracting("errorCode")
        .isEqualTo(com.dershane.userservice.shared.domain.ErrorCode.CONCURRENT_MODIFICATION);
  }

  @Test
  void studentCanHaveOnlyOneGuardian() {
    Student student = newStudent(branch, "1003");
    Guardian mother = newGuardian(branch);
    Guardian father = newGuardian(branch);
    studentGuardians.save(
        StudentGuardian.link(mother.getId(), student.getId(), Relationship.MOTHER));
    entityManager.flush();

    assertViolates(
        () -> {
          studentGuardians.save(
              StudentGuardian.link(father.getId(), student.getId(), Relationship.FATHER));
          entityManager.flush();
        },
        "uk_student_guardian_student");
  }

  @Test
  void studentNumberIsUniqueWithinABranchOnly() {
    newStudent(branch, "2001");
    Branch otherBranch = newBranch();

    newStudent(otherBranch, "2001");
    assertViolates(() -> newStudent(branch, "2001"), "uk_student_branch_number");
  }

  @Test
  void studentBranchCopyFollowsTheUsersBranch() {
    Student student = newStudent(branch, "3001");
    Branch otherBranch = newBranch();

    jdbc.update(
        "update users set branch_id = ? where id = ?", otherBranch.getId(), student.getId());

    UUID copied =
        jdbc.queryForObject(
            "select branch_id from student where user_id = ?", UUID.class, student.getId());
    assertThat(copied).isEqualTo(otherBranch.getId());
  }

  @Test
  void outboxStoresJsonAndLocksPendingRowsOldestFirst() {
    Instant now = Instant.now();
    OutboxEvent older = pendingEvent(now.minusSeconds(10));
    OutboxEvent newer = pendingEvent(now);
    outbox.saveAndFlush(newer);
    outbox.saveAndFlush(older);

    List<OutboxEvent> batch = outbox.lockNextBatch(10);

    assertThat(batch).extracting(OutboxEvent::getEventId).startsWith(older.getEventId());
    String type =
        jdbc.queryForObject(
            "select jsonb_typeof(payload) from outbox_event where event_id = ?",
            String.class,
            older.getEventId());
    assertThat(type).isEqualTo("object");
  }

  @Test
  void auditColumnsAndVersionAreFilled() {
    Student student = newStudent(branch, "4001");

    assertThat(student.getVersion()).isZero();
    Integer filled =
        jdbc.queryForObject(
            "select count(*) from users where id = ? and created_at is not null"
                + " and updated_at is not null and version = 0",
            Integer.class,
            student.getId());
    assertThat(filled).isEqualTo(1);
  }

  @Test
  void savingAnUpdatedStudentKeepsIdentityFieldsAndBumpsTheVersion() {
    Student student = newStudent(branch, "5001");

    student.updateStudentInfo("5002", 11, TargetExam.LGS);
    student.applyPersonalInfo(new PersonalInfo("Veli", "Demir", "veli@example.com", null));
    Student saved = users.save(student);

    assertThat(saved.getVersion()).isEqualTo(1L);
    assertThat(saved.getStudentNumber()).isEqualTo("5002");
    assertThat(saved.getBranchId()).isEqualTo(branch.getId());
    assertThat(saved.getInfo().email()).isEqualTo("veli@example.com");
  }

  private Branch newBranch() {
    String code = "B" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    Branch saved = branches.save(Branch.create("Şube " + code, code));
    entityManager.flush();
    return saved;
  }

  private Student newStudent(Branch owner, String number) {
    UUID id = UUID.randomUUID();
    return users.save(
        Student.register(
            id,
            owner.getId(),
            new PersonalInfo("Ali", "Kaya", null, null),
            "u-" + id,
            number,
            12,
            TargetExam.YKS));
  }

  private Guardian newGuardian(Branch owner) {
    UUID id = UUID.randomUUID();
    return users.save(
        Guardian.register(
            id, owner.getId(), new PersonalInfo("Ayşe", "Kaya", null, "05551112233"), "g-" + id));
  }

  private static OutboxEvent pendingEvent(Instant createdAt) {
    return OutboxEvent.pending(
        UUID.randomUUID(), "UserUpdated", UUID.randomUUID(), "{\"a\":1}", null, createdAt);
  }

  private static void assertViolates(Runnable action, String constraint) {
    assertThatThrownBy(action::run)
        .isInstanceOf(RuntimeException.class)
        .satisfies(
            ex -> {
              Throwable cause = ex;
              while (cause != null && !(cause instanceof ConstraintViolationException)) {
                cause = cause.getCause();
              }
              assertThat(cause).isNotNull();
              assertThat(((ConstraintViolationException) cause).getConstraintName())
                  .isEqualToIgnoringCase(constraint);
            });
  }
}
