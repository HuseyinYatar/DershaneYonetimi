package com.dershane.attendanceservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dershane.attendanceservice.attendance.application.port.in.AttendanceRecordView;
import com.dershane.attendanceservice.attendance.application.port.out.LoadRecordPort;
import com.dershane.attendanceservice.attendance.application.port.out.RecordFilter;
import com.dershane.attendanceservice.attendance.application.port.out.RecordQueryPort;
import com.dershane.attendanceservice.attendance.application.port.out.SaveRecordChangePort;
import com.dershane.attendanceservice.attendance.application.port.out.SaveRecordPort;
import com.dershane.attendanceservice.attendance.domain.AttendanceRecord;
import com.dershane.attendanceservice.attendance.domain.AttendanceRecordChange;
import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import com.dershane.attendanceservice.excuse.application.port.out.LoadExcusePort;
import com.dershane.attendanceservice.excuse.application.port.out.SaveExcusePort;
import com.dershane.attendanceservice.excuse.domain.Excuse;
import com.dershane.attendanceservice.roster.application.port.in.RosterEntryView;
import com.dershane.attendanceservice.roster.application.port.out.LoadGuardianLinkPort;
import com.dershane.attendanceservice.roster.application.port.out.RosterQueryPort;
import com.dershane.attendanceservice.roster.application.port.out.SaveGuardianLinkPort;
import com.dershane.attendanceservice.roster.application.port.out.SaveMembershipReplicaPort;
import com.dershane.attendanceservice.roster.domain.GuardianLinkReplica;
import com.dershane.attendanceservice.roster.domain.MembershipReplica;
import com.dershane.attendanceservice.schedule.application.port.out.LoadSchedulePort;
import com.dershane.attendanceservice.schedule.application.port.out.SaveSchedulePort;
import com.dershane.attendanceservice.schedule.domain.ClassSchedule;
import com.dershane.attendanceservice.session.application.port.out.LoadSessionPort;
import com.dershane.attendanceservice.session.application.port.out.SaveSessionPort;
import com.dershane.attendanceservice.session.domain.ClassSession;
import com.dershane.attendanceservice.session.domain.SessionStatus;
import com.dershane.attendanceservice.shared.domain.ConstraintErrorCodes;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.support.TestContainers;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The queries the unit tests can only mock, against the real PostgreSQL schema: roster dates,
 * overlap checks, the absence sub-select, the SQL read model and the constraints that catch
 * concurrent requests. Runs the real Flyway migration; {@code ddl-auto=validate} also checks that
 * the entity mapping matches the schema.
 */
@DataJpaTest(
    includeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.dershane\\.attendanceservice\\..*\\.adapter\\.out\\.persistence\\..*"))
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryQueriesIT {

  private static final LocalDate DAY = LocalDate.of(2026, 10, 30);

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> TestContainers.postgres().getJdbcUrl());
    registry.add("spring.datasource.username", () -> TestContainers.postgres().getUsername());
    registry.add("spring.datasource.password", () -> TestContainers.postgres().getPassword());
  }

  @Autowired private SaveSessionPort saveSession;
  @Autowired private LoadSessionPort loadSession;
  @Autowired private SaveRecordPort saveRecords;
  @Autowired private LoadRecordPort loadRecords;
  @Autowired private SaveRecordChangePort saveChanges;
  @Autowired private RecordQueryPort recordQuery;
  @Autowired private SaveMembershipReplicaPort saveMemberships;
  @Autowired private RosterQueryPort rosterQuery;
  @Autowired private SaveGuardianLinkPort saveLinks;
  @Autowired private LoadGuardianLinkPort loadLinks;
  @Autowired private SaveExcusePort saveExcuse;
  @Autowired private LoadExcusePort loadExcuse;
  @Autowired private SaveSchedulePort saveSchedule;
  @Autowired private LoadSchedulePort loadSchedule;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  private final UUID branchId = UUID.randomUUID();
  private final UUID classroomId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();
  private final UUID subjectId = UUID.randomUUID();

  private ClassSession session(UUID classroom, UUID teacher, LocalDate day, int hour) {
    return saveSession.save(
        ClassSession.plan(
            null,
            branchId,
            classroom,
            teacher,
            subjectId,
            day,
            LocalTime.of(hour, 0),
            LocalTime.of(hour, 40)));
  }

  private AttendanceRecord record(ClassSession session, UUID studentId, AttendanceStatus status) {
    return saveRecords
        .saveAll(
            List.of(
                AttendanceRecord.mark(
                    session.getId(),
                    studentId,
                    status,
                    null,
                    null,
                    Instant.parse("2026-10-30T07:00:00Z"),
                    teacherId)))
        .getFirst();
  }

  private ClassSession completed(UUID classroom, UUID teacher, LocalDate day, int hour) {
    ClassSession session = session(classroom, teacher, day, hour);
    session.complete(teacher, Instant.now());
    return saveSession.save(session);
  }

  private MembershipReplica membership(
      UUID classroom, UUID student, LocalDate start, LocalDate end) {
    MembershipReplica membership =
        MembershipReplica.open(
            UUID.randomUUID(),
            classroom,
            student,
            branchId,
            start,
            "N-" + student.toString().substring(0, 8),
            "Name");
    if (end != null) {
      membership.close(end);
    }
    saveMemberships.save(membership);
    entityManager.flush();
    return membership;
  }

  @Test
  void rosterCoversMembershipDatesInclusive() {
    UUID student = UUID.randomUUID();
    membership(classroomId, student, DAY, DAY.plusDays(5));

    assertThat(rosterQuery.findRosterOn(classroomId, DAY.minusDays(1))).isEmpty();
    assertThat(rosterQuery.findRosterOn(classroomId, DAY)).hasSize(1);
    assertThat(rosterQuery.findRosterOn(classroomId, DAY.plusDays(5))).hasSize(1);
    assertThat(rosterQuery.findRosterOn(classroomId, DAY.plusDays(6))).isEmpty();
    assertThat(rosterQuery.existsMembershipOn(classroomId, student, DAY.plusDays(5))).isTrue();
    assertThat(rosterQuery.existsMembershipOn(classroomId, student, DAY.plusDays(6))).isFalse();
    assertThat(rosterQuery.existsMembershipOn(UUID.randomUUID(), student, DAY)).isFalse();
  }

  @Test
  void aStudentWhoRejoinedOnTheDayTheyLeftAppearsOnce() {
    UUID student = UUID.randomUUID();
    membership(classroomId, student, DAY.minusDays(10), DAY);
    membership(classroomId, student, DAY, null);

    List<RosterEntryView> roster = rosterQuery.findRosterOn(classroomId, DAY);

    assertThat(roster).extracting(RosterEntryView::studentId).containsExactly(student);
  }

  @Test
  void aGuardianLinkIsReplacedAndFound() {
    UUID student = UUID.randomUUID();
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    saveLinks.save(GuardianLinkReplica.of(student, first));
    entityManager.flush();
    entityManager.clear();

    GuardianLinkReplica loaded = loadLinks.findByStudentId(student).orElseThrow();
    saveLinks.save(new GuardianLinkReplica(student, loaded.getVersion(), second));
    entityManager.flush();

    assertThat(loadLinks.existsByGuardianIdAndStudentId(second, student)).isTrue();
    assertThat(loadLinks.existsByGuardianIdAndStudentId(first, student)).isFalse();
    saveLinks.deleteByStudentId(student);
    entityManager.flush();
    assertThat(loadLinks.findByStudentId(student)).isEmpty();
  }

  @Test
  void overlapChecksSeeOnlyPlannedAndCompletedSessionsAndHonourTheExclusion() {
    ClassSession planned = session(classroomId, teacherId, DAY, 9);

    assertThat(
            loadSession.existsClassroomOverlap(
                classroomId, DAY, LocalTime.of(9, 30), LocalTime.of(10, 10), null))
        .isTrue();
    assertThat(
            loadSession.existsTeacherOverlap(
                teacherId, DAY, LocalTime.of(8, 30), LocalTime.of(9, 10), null))
        .isTrue();
    // Back to back is not an overlap.
    assertThat(
            loadSession.existsClassroomOverlap(
                classroomId, DAY, LocalTime.of(9, 40), LocalTime.of(10, 20), null))
        .isFalse();
    assertThat(
            loadSession.existsClassroomOverlap(
                classroomId, DAY.plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 40), null))
        .isFalse();
    // A session does not clash with itself (reassigning the teacher).
    assertThat(
            loadSession.existsTeacherOverlap(
                teacherId, DAY, LocalTime.of(9, 0), LocalTime.of(9, 40), planned.getId()))
        .isFalse();

    planned.cancel();
    saveSession.save(planned);
    entityManager.flush();

    assertThat(
            loadSession.existsClassroomOverlap(
                classroomId, DAY, LocalTime.of(9, 0), LocalTime.of(9, 40), null))
        .isFalse();
  }

  @Test
  void onlyPlannedSessionsOfPastDaysAreFoundForExpiry() {
    ClassSession past = session(classroomId, teacherId, DAY.minusDays(2), 9);
    ClassSession today = session(UUID.randomUUID(), UUID.randomUUID(), DAY, 9);
    ClassSession pastCompleted =
        completed(UUID.randomUUID(), UUID.randomUUID(), DAY.minusDays(3), 9);

    List<ClassSession> found = loadSession.findPlannedBefore(DAY);

    assertThat(found).extracting(ClassSession::getId).contains(past.getId());
    assertThat(found)
        .extracting(ClassSession::getId)
        .doesNotContain(today.getId(), pastCompleted.getId());
  }

  @Test
  void aScheduleCannotHaveTwoSessionsOnTheSameDay() {
    ClassSchedule schedule =
        saveSchedule.save(
            ClassSchedule.create(
                branchId,
                classroomId,
                teacherId,
                subjectId,
                DayOfWeek.FRIDAY,
                LocalTime.of(9, 0),
                LocalTime.of(9, 40),
                DAY,
                null));
    entityManager.flush();
    saveSession.save(
        ClassSession.plan(
            schedule.getId(),
            branchId,
            classroomId,
            teacherId,
            subjectId,
            DAY,
            LocalTime.of(9, 0),
            LocalTime.of(9, 40)));

    assertThat(loadSession.existsByScheduleIdAndSessionDate(schedule.getId(), DAY)).isTrue();
    assertViolates(
        () ->
            saveSession.save(
                ClassSession.plan(
                    schedule.getId(),
                    branchId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    subjectId,
                    DAY,
                    LocalTime.of(14, 0),
                    LocalTime.of(14, 40))),
        "uk_session_schedule_date");
  }

  @Test
  void activeSchedulesAreFoundByValidityRange() {
    ClassSchedule open = saveSchedule.save(schedule(DAY.minusMonths(1), null));
    ClassSchedule ended = saveSchedule.save(schedule(DAY.minusMonths(2), DAY.minusDays(1)));
    ClassSchedule later = saveSchedule.save(schedule(DAY.plusDays(8), null));
    ClassSchedule inactive = schedule(DAY.minusMonths(1), null);
    inactive.deactivate();
    ClassSchedule inactiveSaved = saveSchedule.save(inactive);
    entityManager.flush();

    List<UUID> ids =
        loadSchedule.findActiveValidBetween(DAY, DAY.plusDays(7)).stream()
            .map(ClassSchedule::getId)
            .toList();

    assertThat(ids).contains(open.getId());
    assertThat(ids).doesNotContain(ended.getId(), later.getId(), inactiveSaved.getId());
  }

  @Test
  void aStudentHasOneRecordPerSession() {
    ClassSession session = session(classroomId, teacherId, DAY, 9);
    UUID student = UUID.randomUUID();
    record(session, student, AttendanceStatus.PRESENT);

    assertViolates(
        () -> record(session, student, AttendanceStatus.ABSENT),
        "uk_attendance_record_session_student");
    assertThat(ConstraintErrorCodes.forConstraint("uk_attendance_record_session_student"))
        .contains(ErrorCode.ATTENDANCE_ALREADY_TAKEN);
    assertThat(ConstraintErrorCodes.forConstraint("uk_session_schedule_date"))
        .contains(ErrorCode.DATA_CONFLICT);
  }

  @Test
  void absencesOfCompletedSessionsInTheRangeAreFoundForAnExcuse() {
    UUID student = UUID.randomUUID();
    ClassSession inRange = completed(classroomId, teacherId, DAY, 9);
    ClassSession outOfRange = completed(UUID.randomUUID(), UUID.randomUUID(), DAY.minusDays(10), 9);
    ClassSession planned = session(UUID.randomUUID(), UUID.randomUUID(), DAY, 11);
    AttendanceRecord absent = record(inRange, student, AttendanceStatus.ABSENT);
    record(outOfRange, student, AttendanceStatus.ABSENT);
    record(planned, student, AttendanceStatus.ABSENT);
    ClassSession otherPresent = completed(UUID.randomUUID(), UUID.randomUUID(), DAY, 13);
    record(otherPresent, student, AttendanceStatus.PRESENT);

    List<AttendanceRecord> found =
        loadRecords.findAbsencesInCompletedSessions(student, DAY.minusDays(1), DAY.plusDays(1));

    assertThat(found).extracting(AttendanceRecord::getId).containsExactly(absent.getId());
  }

  @Test
  void anExcuseChangeIsSavedWithItsAuditLine() {
    UUID student = UUID.randomUUID();
    UUID admin = UUID.randomUUID();
    ClassSession session = completed(classroomId, teacherId, DAY, 9);
    AttendanceRecord absent = record(session, student, AttendanceStatus.ABSENT);
    Excuse excuse = approvedExcuse(student, DAY, DAY);
    entityManager.flush();
    entityManager.clear();

    AttendanceRecord loaded =
        loadRecords.findBySessionIdAndStudentId(session.getId(), student).orElseThrow();
    loaded.changeTo(AttendanceStatus.EXCUSED, excuse.getId(), Instant.now(), admin);
    saveRecords.saveAll(List.of(loaded));
    saveChanges.append(
        List.of(
            AttendanceRecordChange.of(
                absent.getId(),
                AttendanceStatus.ABSENT,
                AttendanceStatus.EXCUSED,
                admin,
                "Excuse approved",
                Instant.now())));
    entityManager.flush();
    entityManager.clear();

    AttendanceRecord reloaded =
        loadRecords.findBySessionIdAndStudentId(session.getId(), student).orElseThrow();
    assertThat(reloaded.getStatus()).isEqualTo(AttendanceStatus.EXCUSED);
    assertThat(reloaded.getExcuseId()).isEqualTo(excuse.getId());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from attendance_record_change where record_id = ?",
                Long.class,
                absent.getId()))
        .isEqualTo(1L);
  }

  @Test
  void aStaleRecordCannotOverwriteANewerOne() {
    ClassSession session = completed(classroomId, teacherId, DAY, 9);
    UUID student = UUID.randomUUID();
    record(session, student, AttendanceStatus.PRESENT);
    entityManager.flush();
    entityManager.clear();
    AttendanceRecord first =
        loadRecords.findBySessionIdAndStudentId(session.getId(), student).orElseThrow();
    AttendanceRecord second =
        loadRecords.findBySessionIdAndStudentId(session.getId(), student).orElseThrow();

    first.changeTo(AttendanceStatus.ABSENT, null, Instant.now(), teacherId);
    saveRecords.saveAll(List.of(first));
    entityManager.flush();
    entityManager.clear();
    second.changeTo(AttendanceStatus.LATE, null, Instant.now(), teacherId);

    assertThatThrownBy(() -> saveRecords.saveAll(List.of(second)))
        .isInstanceOf(DomainException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
  }

  @Test
  void theEarliestDecidedApprovedExcuseWinsAndPendingOnesAreIgnored() throws InterruptedException {
    UUID student = UUID.randomUUID();
    Excuse first = approvedExcuse(student, DAY.minusDays(1), DAY.plusDays(1));
    Thread.sleep(5);
    approvedExcuse(student, DAY, DAY);
    saveExcuse.save(Excuse.request(student, branchId, DAY, DAY, "pending one", UUID.randomUUID()));
    UUID other = UUID.randomUUID();
    entityManager.flush();

    Map<UUID, UUID> coverage = loadExcuse.findApprovedCoverage(List.of(student, other), DAY);

    assertThat(coverage).containsOnlyKeys(student).containsEntry(student, first.getId());
    assertThat(loadExcuse.findApprovedCoverage(List.of(student), DAY.plusDays(2))).isEmpty();
  }

  @Test
  void theRecordReadModelFiltersByScopeDatesAndPagesNewestFirst() {
    UUID student = UUID.randomUUID();
    UUID otherTeacher = UUID.randomUUID();
    UUID otherClassroom = UUID.randomUUID();
    ClassSession older = completed(classroomId, teacherId, DAY.minusDays(1), 9);
    ClassSession newer = completed(classroomId, teacherId, DAY, 9);
    ClassSession foreign = completed(otherClassroom, otherTeacher, DAY, 11);
    record(older, student, AttendanceStatus.PRESENT);
    record(newer, student, AttendanceStatus.ABSENT);
    record(foreign, student, AttendanceStatus.LATE);
    UUID noise = UUID.randomUUID();
    record(newer, noise, AttendanceStatus.PRESENT);
    entityManager.flush();

    Page<AttendanceRecordView> all =
        recordQuery.findViews(
            new RecordFilter(branchId, null, student, null, null, null), PageRequest.of(0, 10));
    assertThat(all.getTotalElements()).isEqualTo(3);
    assertThat(all.getContent())
        .extracting(AttendanceRecordView::sessionDate)
        .isSortedAccordingTo(java.util.Comparator.reverseOrder());

    Page<AttendanceRecordView> ofTeacher =
        recordQuery.findViews(
            new RecordFilter(null, teacherId, student, null, null, null), PageRequest.of(0, 10));
    assertThat(ofTeacher.getContent())
        .extracting(AttendanceRecordView::status)
        .containsExactlyInAnyOrder(AttendanceStatus.PRESENT, AttendanceStatus.ABSENT);

    Page<AttendanceRecordView> inRange =
        recordQuery.findViews(
            new RecordFilter(null, null, student, classroomId, DAY, DAY), PageRequest.of(0, 10));
    assertThat(inRange.getContent())
        .singleElement()
        .satisfies(
            view -> {
              assertThat(view.status()).isEqualTo(AttendanceStatus.ABSENT);
              assertThat(view.classroomId()).isEqualTo(classroomId);
              assertThat(view.subjectId()).isEqualTo(subjectId);
              assertThat(view.sessionDate()).isEqualTo(DAY);
              assertThat(view.startsAt()).isEqualTo(LocalTime.of(9, 0));
              assertThat(view.endsAt()).isEqualTo(LocalTime.of(9, 40));
              assertThat(view.markedAt()).isEqualTo(Instant.parse("2026-10-30T07:00:00Z"));
              assertThat(view.markedBy()).isEqualTo(teacherId);
            });

    Page<AttendanceRecordView> firstPage =
        recordQuery.findViews(
            new RecordFilter(null, null, student, null, null, null), PageRequest.of(0, 2));
    Page<AttendanceRecordView> secondPage =
        recordQuery.findViews(
            new RecordFilter(null, null, student, null, null, null), PageRequest.of(1, 2));
    assertThat(firstPage.getContent()).hasSize(2);
    assertThat(secondPage.getContent()).hasSize(1);
    assertThat(firstPage.getTotalElements()).isEqualTo(3);
  }

  @Test
  void statusCountsAndSingleViewsComeFromTheSameJoin() {
    UUID student = UUID.randomUUID();
    ClassSession one = completed(classroomId, teacherId, DAY, 9);
    ClassSession two = completed(UUID.randomUUID(), UUID.randomUUID(), DAY, 11);
    ClassSession three = completed(UUID.randomUUID(), UUID.randomUUID(), DAY, 13);
    AttendanceRecord present = record(one, student, AttendanceStatus.PRESENT);
    record(two, student, AttendanceStatus.ABSENT);
    record(three, student, AttendanceStatus.ABSENT);
    entityManager.flush();

    Map<AttendanceStatus, Long> counts =
        recordQuery.countByStatus(new RecordFilter(null, null, student, null, DAY, DAY));

    assertThat(counts)
        .containsEntry(AttendanceStatus.PRESENT, 1L)
        .containsEntry(AttendanceStatus.ABSENT, 2L)
        .doesNotContainKey(AttendanceStatus.LATE);
    assertThat(recordQuery.findViewById(present.getId()))
        .get()
        .extracting(AttendanceRecordView::sessionId)
        .isEqualTo(one.getId());
    assertThat(recordQuery.findViewById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void anUnknownSessionStatusColumnValueIsRejectedByTheSchema() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into class_session (id, branch_id, classroom_id, teacher_id,"
                        + " subject_id, session_date, starts_at, ends_at, status, created_at,"
                        + " updated_at, version) values (?, ?, ?, ?, ?, ?, '09:00', '09:40',"
                        + " 'BOGUS', now(), now(), 0)",
                    UUID.randomUUID(),
                    branchId,
                    classroomId,
                    teacherId,
                    subjectId,
                    DAY))
        .isInstanceOf(RuntimeException.class);
    assertThat(SessionStatus.valueOf("PLANNED")).isNotNull();
  }

  private ClassSchedule schedule(LocalDate validFrom, LocalDate validTo) {
    return ClassSchedule.create(
        branchId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        subjectId,
        DayOfWeek.FRIDAY,
        LocalTime.of(9, 0),
        LocalTime.of(9, 40),
        validFrom,
        validTo);
  }

  private Excuse approvedExcuse(UUID student, LocalDate from, LocalDate to) {
    Excuse excuse =
        saveExcuse.save(Excuse.request(student, branchId, from, to, "Illness", UUID.randomUUID()));
    excuse.approve(UUID.randomUUID(), Instant.now(), null);
    return saveExcuse.save(excuse);
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
