package com.dershane.attendanceservice.session.adapter.in.web;

import static com.dershane.attendanceservice.support.TestJwts.token;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.attendanceservice.session.application.port.in.CancelSessionUseCase;
import com.dershane.attendanceservice.session.application.port.in.CreateAdHocSessionUseCase;
import com.dershane.attendanceservice.session.application.port.in.GetSessionQuery;
import com.dershane.attendanceservice.session.application.port.in.ListSessionsQuery;
import com.dershane.attendanceservice.session.application.port.in.ReassignTeacherUseCase;
import com.dershane.attendanceservice.session.application.port.in.SessionView;
import com.dershane.attendanceservice.session.domain.SessionStatus;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.support.WebMvcTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SessionController.class)
@Import({WebMvcTestSupport.class, SessionWebMapperImpl.class})
class SessionControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private CreateAdHocSessionUseCase createSession;
  @MockitoBean private CancelSessionUseCase cancelSession;
  @MockitoBean private ReassignTeacherUseCase reassignTeacher;
  @MockitoBean private GetSessionQuery getSession;
  @MockitoBean private ListSessionsQuery listSessions;

  private final UUID branchId = UUID.randomUUID();
  private final UUID sessionId = UUID.randomUUID();

  private String body() {
    return "{\"branchId\":\""
        + branchId
        + "\",\"classroomId\":\""
        + UUID.randomUUID()
        + "\",\"teacherId\":\""
        + UUID.randomUUID()
        + "\",\"subjectId\":\""
        + UUID.randomUUID()
        + "\",\"sessionDate\":\"2026-11-02\",\"startsAt\":\"14:00\",\"endsAt\":\"14:40\"}";
  }

  private SessionView view(SessionStatus status) {
    return new SessionView(
        sessionId,
        null,
        branchId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        LocalDate.of(2026, 11, 2),
        LocalTime.of(14, 0),
        LocalTime.of(14, 40),
        status,
        null,
        null,
        Instant.now(),
        Instant.now());
  }

  @Test
  void teachersCannotOpenSessions() throws Exception {
    mvc.perform(
            post("/api/v1/class-sessions")
                .with(token(UUID.randomUUID(), branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    verifyNoInteractions(createSession);
  }

  @Test
  void anAdminOpensASessionAndGetsItsLocation() throws Exception {
    when(createSession.create(any(), any())).thenReturn(view(SessionStatus.PLANNED));

    mvc.perform(
            post("/api/v1/class-sessions")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/class-sessions/" + sessionId))
        .andExpect(jsonPath("$.status").value("PLANNED"))
        .andExpect(jsonPath("$.startsAt").value("14:00:00"));
  }

  @Test
  void missingFieldsAreABadRequest() throws Exception {
    mvc.perform(
            post("/api/v1/class-sessions")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":\"" + branchId + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    verifyNoInteractions(createSession);
  }

  @Test
  void anOverlapIsAConflictInTurkish() throws Exception {
    when(createSession.create(any(), any()))
        .thenThrow(new DomainException(ErrorCode.SESSION_CLASSROOM_OVERLAP));

    mvc.perform(
            post("/api/v1/class-sessions")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Sınıfın bu saatte başka bir oturumu var."));
  }

  @Test
  void studentsAndGuardiansCannotListSessions() throws Exception {
    for (String role : new String[] {"student", "guardian"}) {
      mvc.perform(get("/api/v1/class-sessions").with(token(UUID.randomUUID(), branchId, role)))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(listSessions);
  }

  @Test
  void teachersListWithDateAndStatusFilters() throws Exception {
    when(listSessions.list(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Page.empty());

    mvc.perform(
            get("/api/v1/class-sessions")
                .param("from", "2026-11-01")
                .param("to", "2026-11-07")
                .param("status", "PLANNED")
                .with(token(UUID.randomUUID(), branchId, "teacher")))
        .andExpect(status().isOk());

    verify(listSessions)
        .list(
            any(),
            eq(null),
            eq(null),
            eq(null),
            eq(LocalDate.of(2026, 11, 1)),
            eq(LocalDate.of(2026, 11, 7)),
            eq(SessionStatus.PLANNED),
            any());
  }

  @Test
  void onlyAdminsCancelAndReassign() throws Exception {
    mvc.perform(
            post("/api/v1/class-sessions/{id}/cancel", sessionId)
                .with(token(UUID.randomUUID(), branchId, "teacher")))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/v1/class-sessions/{id}/teacher", sessionId)
                .with(token(UUID.randomUUID(), branchId, "teacher"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teacherId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(cancelSession, reassignTeacher);
  }

  @Test
  void anAdminCancelsAndReassigns() throws Exception {
    UUID substitute = UUID.randomUUID();
    when(cancelSession.cancel(any(), eq(sessionId))).thenReturn(view(SessionStatus.CANCELLED));
    when(reassignTeacher.reassign(any(), eq(sessionId), eq(substitute)))
        .thenReturn(view(SessionStatus.PLANNED));

    mvc.perform(
            post("/api/v1/class-sessions/{id}/cancel", sessionId)
                .with(token(UUID.randomUUID(), branchId, "admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    mvc.perform(
            put("/api/v1/class-sessions/{id}/teacher", sessionId)
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teacherId\":\"" + substitute + "\"}"))
        .andExpect(status().isOk());
  }

  @Test
  void aMissingSessionIsNotFound() throws Exception {
    when(getSession.get(any(), eq(sessionId)))
        .thenThrow(new DomainException(ErrorCode.SESSION_NOT_FOUND));

    mvc.perform(
            get("/api/v1/class-sessions/{id}", sessionId)
                .with(token(UUID.randomUUID(), branchId, "teacher")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_FOUND"));
  }
}
