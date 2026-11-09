package com.dershane.attendanceservice.attendance.adapter.in.web;

import static com.dershane.attendanceservice.support.TestJwts.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.attendanceservice.attendance.application.port.in.AttendanceRecordView;
import com.dershane.attendanceservice.attendance.application.port.in.AttendanceSubmissionView;
import com.dershane.attendanceservice.attendance.application.port.in.CorrectRecordCommand;
import com.dershane.attendanceservice.attendance.application.port.in.CorrectRecordUseCase;
import com.dershane.attendanceservice.attendance.application.port.in.RosterRowView;
import com.dershane.attendanceservice.attendance.application.port.in.SessionRosterQuery;
import com.dershane.attendanceservice.attendance.application.port.in.SessionRosterView;
import com.dershane.attendanceservice.attendance.application.port.in.TakeAttendanceCommand;
import com.dershane.attendanceservice.attendance.application.port.in.TakeAttendanceUseCase;
import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import com.dershane.attendanceservice.session.domain.SessionStatus;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.support.WebMvcTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AttendanceController.class)
@Import({WebMvcTestSupport.class, AttendanceWebMapperImpl.class})
class AttendanceControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private TakeAttendanceUseCase takeAttendance;
  @MockitoBean private CorrectRecordUseCase correctRecord;
  @MockitoBean private SessionRosterQuery sessionRoster;

  private final UUID sessionId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID teacherId = UUID.randomUUID();
  private final UUID branchId = UUID.randomUUID();

  private String takeBody() {
    return "{\"entries\":[{\"studentId\":\""
        + studentId
        + "\",\"status\":\"ABSENT\",\"note\":\"sick\"}],\"reason\":null}";
  }

  @Test
  void requestWithoutTokenGets401() throws Exception {
    mvc.perform(
            post("/api/v1/class-sessions/{id}/attendance", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(takeBody()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    verifyNoInteractions(takeAttendance);
  }

  @Test
  void studentsAndGuardiansCannotTakeAttendance() throws Exception {
    for (String role : List.of("student", "guardian")) {
      mvc.perform(
              post("/api/v1/class-sessions/{id}/attendance", sessionId)
                  .with(token(UUID.randomUUID(), branchId, role))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(takeBody()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
          .andExpect(jsonPath("$.detail").value("Bu işlem için yetkiniz yok."));
    }
    verifyNoInteractions(takeAttendance);
  }

  @Test
  void aTeacherSubmitsTheFullListAndGetsTheCounts() throws Exception {
    when(takeAttendance.take(any(), any()))
        .thenReturn(new AttendanceSubmissionView(sessionId, Instant.now(), 0, 1, 0, 0));

    mvc.perform(
            post("/api/v1/class-sessions/{id}/attendance", sessionId)
                .with(token(teacherId, branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(takeBody()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
        .andExpect(jsonPath("$.absent").value(1));

    ArgumentCaptor<TakeAttendanceCommand> command =
        ArgumentCaptor.forClass(TakeAttendanceCommand.class);
    verify(takeAttendance).take(any(), command.capture());
    assertThat(command.getValue().sessionId()).isEqualTo(sessionId);
    assertThat(command.getValue().entries())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.studentId()).isEqualTo(studentId);
              assertThat(entry.status()).isEqualTo(AttendanceStatus.ABSENT);
              assertThat(entry.note()).isEqualTo("sick");
            });
  }

  @Test
  void anEmptyListIsABadRequest() throws Exception {
    mvc.perform(
            post("/api/v1/class-sessions/{id}/attendance", sessionId)
                .with(token(teacherId, branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entries\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    verifyNoInteractions(takeAttendance);
  }

  @Test
  void aListLargerThanAnyClassIsABadRequest() throws Exception {
    String entry = "{\"studentId\":\"" + studentId + "\",\"status\":\"PRESENT\"}";
    String body =
        "{\"entries\":[" + String.join(",", java.util.Collections.nCopies(501, entry)) + "]}";
    mvc.perform(
            post("/api/v1/class-sessions/{id}/attendance", sessionId)
                .with(token(teacherId, branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    verifyNoInteractions(takeAttendance);
  }

  @Test
  void anUnknownStatusIsABadRequest() throws Exception {
    mvc.perform(
            post("/api/v1/class-sessions/{id}/attendance", sessionId)
                .with(token(teacherId, branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"entries\":[{\"studentId\":\"" + studentId + "\",\"status\":\"SKIPPED\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));
    verifyNoInteractions(takeAttendance);
  }

  @Test
  void aRosterMismatchBecomesAnUnprocessableProblemInTurkish() throws Exception {
    when(takeAttendance.take(any(), any()))
        .thenThrow(new DomainException(ErrorCode.ROSTER_MISMATCH));

    mvc.perform(
            post("/api/v1/class-sessions/{id}/attendance", sessionId)
                .with(token(teacherId, branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(takeBody()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("ROSTER_MISMATCH"))
        .andExpect(jsonPath("$.detail").value(containsString("her birini yalnızca bir kez")));
  }

  @Test
  void aSecondSubmissionIsAConflictAndEnglishClientsGetEnglish() throws Exception {
    when(takeAttendance.take(any(), any()))
        .thenThrow(new DomainException(ErrorCode.ATTENDANCE_ALREADY_TAKEN));

    mvc.perform(
            post("/api/v1/class-sessions/{id}/attendance", sessionId)
                .with(token(teacherId, branchId, "teacher"))
                .header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON)
                .content(takeBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("ATTENDANCE_ALREADY_TAKEN"))
        .andExpect(jsonPath("$.detail").value("Attendance was already taken for this session."));
  }

  @Test
  void theCallerIsResolvedFromTheToken() throws Exception {
    when(takeAttendance.take(any(), any()))
        .thenReturn(new AttendanceSubmissionView(sessionId, Instant.now(), 1, 0, 0, 0));

    mvc.perform(
            post("/api/v1/class-sessions/{id}/attendance", sessionId)
                .with(token(teacherId, branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(takeBody()))
        .andExpect(status().isCreated());

    verify(takeAttendance)
        .take(
            org.mockito.ArgumentMatchers.argThat(
                actor -> actor.id().equals(teacherId) && actor.isTeacher()),
            any());
  }

  @Test
  void theRosterListsStudentsWithTheirStatus() throws Exception {
    when(sessionRoster.rosterOf(any(), eq(sessionId)))
        .thenReturn(
            new SessionRosterView(
                sessionId,
                SessionStatus.PLANNED,
                List.of(new RosterRowView(studentId, "1042", "Ali Kaya", null, true, null))));

    mvc.perform(
            get("/api/v1/class-sessions/{id}/roster", sessionId)
                .with(token(teacherId, branchId, "teacher")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionStatus").value("PLANNED"))
        .andExpect(jsonPath("$.students[0].studentId").value(studentId.toString()))
        .andExpect(jsonPath("$.students[0].excused").value(true))
        .andExpect(jsonPath("$.students[0].status").isEmpty());
  }

  @Test
  void aCorrectionNeedsAReason() throws Exception {
    mvc.perform(
            put("/api/v1/class-sessions/{id}/records/{studentId}", sessionId, studentId)
                .with(token(teacherId, branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PRESENT\",\"reason\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    verifyNoInteractions(correctRecord);
  }

  @Test
  void aCorrectionPassesSessionStudentStatusAndReason() throws Exception {
    when(correctRecord.correct(any(), any()))
        .thenReturn(
            new AttendanceRecordView(
                UUID.randomUUID(),
                sessionId,
                studentId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.of(2026, 10, 30),
                LocalTime.of(9, 0),
                LocalTime.of(9, 40),
                AttendanceStatus.PRESENT,
                null,
                null,
                Instant.now(),
                teacherId));

    mvc.perform(
            put("/api/v1/class-sessions/{id}/records/{studentId}", sessionId, studentId)
                .with(token(teacherId, branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PRESENT\",\"reason\":\"Was here\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PRESENT"));

    ArgumentCaptor<CorrectRecordCommand> command =
        ArgumentCaptor.forClass(CorrectRecordCommand.class);
    verify(correctRecord).correct(any(), command.capture());
    assertThat(command.getValue().sessionId()).isEqualTo(sessionId);
    assertThat(command.getValue().studentId()).isEqualTo(studentId);
    assertThat(command.getValue().status()).isEqualTo(AttendanceStatus.PRESENT);
    assertThat(command.getValue().reason()).isEqualTo("Was here");
  }
}
