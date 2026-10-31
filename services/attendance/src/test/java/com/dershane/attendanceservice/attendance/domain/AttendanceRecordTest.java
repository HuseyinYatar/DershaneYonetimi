package com.dershane.attendanceservice.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttendanceRecordTest {

  private static final Instant NOW = Instant.parse("2026-10-30T07:00:00Z");

  private final UUID teacherId = UUID.randomUUID();

  private AttendanceRecord record(AttendanceStatus status, UUID excuseId) {
    return AttendanceRecord.mark(
        UUID.randomUUID(), UUID.randomUUID(), status, excuseId, null, NOW, teacherId);
  }

  @Test
  void onlyAnUnexcusedAbsenceAndATeacherExcuseCallForAnEvent() {
    assertThat(record(AttendanceStatus.ABSENT, null).notificationKind())
        .isEqualTo(NotificationKind.ABSENCE);
    assertThat(record(AttendanceStatus.EXCUSED, null).notificationKind())
        .isEqualTo(NotificationKind.TEACHER_EXCUSE);
    assertThat(record(AttendanceStatus.EXCUSED, UUID.randomUUID()).notificationKind())
        .isEqualTo(NotificationKind.NONE);
    assertThat(record(AttendanceStatus.PRESENT, null).notificationKind())
        .isEqualTo(NotificationKind.NONE);
    assertThat(record(AttendanceStatus.LATE, null).notificationKind())
        .isEqualTo(NotificationKind.NONE);
  }

  @Test
  void changingToTheSameOutcomeChangesNothing() {
    AttendanceRecord record = record(AttendanceStatus.ABSENT, null);

    assertThat(record.changeTo(AttendanceStatus.ABSENT, null, NOW.plusSeconds(60), teacherId))
        .isFalse();
    assertThat(record.getMarkedAt()).isEqualTo(NOW);
  }

  @Test
  void aChangeRecordsWhoMadeItAndWhen() {
    AttendanceRecord record = record(AttendanceStatus.ABSENT, null);
    UUID admin = UUID.randomUUID();
    UUID excuseId = UUID.randomUUID();

    boolean changed =
        record.changeTo(AttendanceStatus.EXCUSED, excuseId, NOW.plusSeconds(60), admin);

    assertThat(changed).isTrue();
    assertThat(record.getStatus()).isEqualTo(AttendanceStatus.EXCUSED);
    assertThat(record.getExcuseId()).isEqualTo(excuseId);
    assertThat(record.getMarkedBy()).isEqualTo(admin);
    assertThat(record.getMarkedAt()).isEqualTo(NOW.plusSeconds(60));
  }

  @Test
  void aBlankNoteIsDropped() {
    AttendanceRecord record =
        AttendanceRecord.mark(
            UUID.randomUUID(),
            UUID.randomUUID(),
            AttendanceStatus.EXCUSED,
            null,
            "  ",
            NOW,
            teacherId);

    assertThat(record.getNote()).isNull();
  }
}
