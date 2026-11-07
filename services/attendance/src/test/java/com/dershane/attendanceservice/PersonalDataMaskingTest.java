package com.dershane.attendanceservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.dershane.attendanceservice.attendance.adapter.in.web.dto.AttendanceEntryRequest;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.AttendanceRecordResponse;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.CorrectRecordRequest;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.RosterRowResponse;
import com.dershane.attendanceservice.attendance.adapter.in.web.dto.TakeAttendanceRequest;
import com.dershane.attendanceservice.attendance.application.port.in.AttendanceRecordView;
import com.dershane.attendanceservice.attendance.application.port.in.CorrectRecordCommand;
import com.dershane.attendanceservice.attendance.application.port.in.RosterRowView;
import com.dershane.attendanceservice.attendance.application.port.in.TakeAttendanceCommand;
import com.dershane.attendanceservice.attendance.domain.AttendanceRecord;
import com.dershane.attendanceservice.attendance.domain.AttendanceRecordChange;
import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import com.dershane.attendanceservice.excuse.adapter.in.web.dto.DecideExcuseRequest;
import com.dershane.attendanceservice.excuse.adapter.in.web.dto.ExcuseResponse;
import com.dershane.attendanceservice.excuse.adapter.in.web.dto.RequestExcuseRequest;
import com.dershane.attendanceservice.excuse.application.port.in.ExcuseView;
import com.dershane.attendanceservice.excuse.application.port.in.RequestExcuseCommand;
import com.dershane.attendanceservice.excuse.domain.Excuse;
import com.dershane.attendanceservice.excuse.domain.ExcuseStatus;
import com.dershane.attendanceservice.roster.application.port.in.RecordMembershipAddedCommand;
import com.dershane.attendanceservice.roster.application.port.in.RosterEntryView;
import com.dershane.attendanceservice.roster.domain.MembershipReplica;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * KVKK: objects carrying personal data may end up in a log line through string concatenation or an
 * exception message. Their {@code toString} must not reveal names, student numbers or free text
 * about a child (excuse reasons, teacher notes, correction reasons).
 */
class PersonalDataMaskingTest {

  private static final String NAME = "Zeynep Öztürk";
  private static final String NUMBER = "998877";
  private static final String TEXT = "Hospital appointment for the flu";
  private static final List<String> SECRETS = List.of(NAME, NUMBER, TEXT);

  private final UUID id = UUID.randomUUID();
  private final LocalDate day = LocalDate.of(2026, 11, 2);
  private final Instant now = Instant.parse("2026-10-30T07:00:00Z");

  private void assertMasked(Object value) {
    assertThat(value.toString()).doesNotContain(SECRETS);
  }

  @Test
  void rosterObjectsAreMasked() {
    assertMasked(new RosterEntryView(id, NUMBER, NAME));
    assertMasked(new RosterRowView(id, NUMBER, NAME, AttendanceStatus.PRESENT, false, null));
    assertMasked(new RosterRowResponse(id, NUMBER, NAME, AttendanceStatus.PRESENT, false, null));
    assertMasked(MembershipReplica.open(id, id, id, id, day, NUMBER, NAME));
    assertMasked(new RecordMembershipAddedCommand(id, id, id, id, NUMBER, NAME, day));
  }

  @Test
  void attendanceObjectsAreMasked() {
    assertMasked(
        new AttendanceRecordView(
            id,
            id,
            id,
            id,
            id,
            day,
            LocalTime.NOON,
            LocalTime.NOON,
            AttendanceStatus.EXCUSED,
            null,
            TEXT,
            now,
            id));
    assertMasked(
        new AttendanceRecordResponse(
            id,
            id,
            id,
            id,
            id,
            day,
            LocalTime.NOON,
            LocalTime.NOON,
            AttendanceStatus.EXCUSED,
            null,
            TEXT,
            now,
            id));
    assertMasked(AttendanceRecord.mark(id, id, AttendanceStatus.EXCUSED, null, TEXT, now, id));
    assertMasked(AttendanceRecordChange.of(id, null, AttendanceStatus.EXCUSED, id, TEXT, now));
    assertMasked(new TakeAttendanceCommand.Entry(id, AttendanceStatus.EXCUSED, TEXT));
    assertMasked(new AttendanceEntryRequest(id, AttendanceStatus.EXCUSED, TEXT));
  }

  @Test
  void wholeRequestsDoNotRevealNotesOrReasons() {
    TakeAttendanceCommand command =
        new TakeAttendanceCommand(
            id, List.of(new TakeAttendanceCommand.Entry(id, AttendanceStatus.EXCUSED, TEXT)), TEXT);
    TakeAttendanceRequest request =
        new TakeAttendanceRequest(
            List.of(new AttendanceEntryRequest(id, AttendanceStatus.EXCUSED, TEXT)), TEXT);

    assertThat(command.toString()).doesNotContain(TEXT);
    assertThat(request.toString()).doesNotContain(TEXT);
    assertThat(new CorrectRecordCommand(id, id, AttendanceStatus.ABSENT, TEXT).toString())
        .doesNotContain(TEXT);
    assertThat(new CorrectRecordRequest(AttendanceStatus.ABSENT, TEXT).toString())
        .doesNotContain(TEXT);
  }

  @Test
  void excuseObjectsAreMasked() {
    assertMasked(Excuse.request(id, id, day, day, TEXT, id));
    assertMasked(new RequestExcuseCommand(id, day, day, TEXT));
    assertMasked(new RequestExcuseRequest(id, day, day, TEXT));
    assertMasked(
        new ExcuseView(id, id, id, day, day, TEXT, ExcuseStatus.REJECTED, id, id, now, TEXT, now));
    assertMasked(
        new ExcuseResponse(
            id, id, id, day, day, TEXT, ExcuseStatus.REJECTED, id, id, now, TEXT, now));
    assertThat(new DecideExcuseRequest(TEXT).toString()).doesNotContain(TEXT);
  }
}
