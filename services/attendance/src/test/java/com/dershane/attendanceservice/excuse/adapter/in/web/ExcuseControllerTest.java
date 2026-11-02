package com.dershane.attendanceservice.excuse.adapter.in.web;

import static com.dershane.attendanceservice.support.TestJwts.token;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.attendanceservice.excuse.application.port.in.DecideExcuseUseCase;
import com.dershane.attendanceservice.excuse.application.port.in.ExcuseView;
import com.dershane.attendanceservice.excuse.application.port.in.ListExcusesQuery;
import com.dershane.attendanceservice.excuse.application.port.in.RequestExcuseUseCase;
import com.dershane.attendanceservice.excuse.domain.ExcuseStatus;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.support.WebMvcTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExcuseController.class)
@Import({WebMvcTestSupport.class, ExcuseWebMapperImpl.class})
class ExcuseControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private RequestExcuseUseCase requestExcuse;
  @MockitoBean private DecideExcuseUseCase decideExcuse;
  @MockitoBean private ListExcusesQuery listExcuses;

  private final UUID branchId = UUID.randomUUID();
  private final UUID studentId = UUID.randomUUID();
  private final UUID excuseId = UUID.randomUUID();

  private String body() {
    return "{\"studentId\":\""
        + studentId
        + "\",\"fromDate\":\"2026-11-02\",\"toDate\":\"2026-11-04\",\"reason\":\"Illness\"}";
  }

  private ExcuseView view(ExcuseStatus status) {
    return new ExcuseView(
        excuseId,
        studentId,
        branchId,
        LocalDate.of(2026, 11, 2),
        LocalDate.of(2026, 11, 4),
        "Illness",
        status,
        UUID.randomUUID(),
        null,
        null,
        null,
        Instant.now());
  }

  @Test
  void onlyGuardiansReportExcuses() throws Exception {
    for (String role : new String[] {"student", "teacher", "admin"}) {
      mvc.perform(
              post("/api/v1/excuses")
                  .with(token(UUID.randomUUID(), branchId, role))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body()))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }
    verifyNoInteractions(requestExcuse);
  }

  @Test
  void aGuardianReportsAnExcuseAndGetsItsLocation() throws Exception {
    when(requestExcuse.request(any(), any())).thenReturn(view(ExcuseStatus.PENDING));

    mvc.perform(
            post("/api/v1/excuses")
                .with(token(UUID.randomUUID(), branchId, "guardian"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/excuses/" + excuseId))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.reason").value("Illness"));
  }

  @Test
  void aMissingReasonIsABadRequest() throws Exception {
    mvc.perform(
            post("/api/v1/excuses")
                .with(token(UUID.randomUUID(), branchId, "guardian"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"studentId\":\""
                        + studentId
                        + "\",\"fromDate\":\"2026-11-02\",\"toDate\":\"2026-11-04\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    verifyNoInteractions(requestExcuse);
  }

  @Test
  void aStrangersChildIsForbidden() throws Exception {
    when(requestExcuse.request(any(), any()))
        .thenThrow(new DomainException(ErrorCode.GUARDIAN_NOT_LINKED));

    mvc.perform(
            post("/api/v1/excuses")
                .with(token(UUID.randomUUID(), branchId, "guardian"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("GUARDIAN_NOT_LINKED"));
  }

  @Test
  void onlyAdminsDecideExcuses() throws Exception {
    mvc.perform(
            post("/api/v1/excuses/{id}/approve", excuseId)
                .with(token(UUID.randomUUID(), branchId, "guardian")))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/excuses/{id}/reject", excuseId)
                .with(token(UUID.randomUUID(), branchId, "teacher")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(decideExcuse);
  }

  @Test
  void anAdminApprovesWithoutABody() throws Exception {
    when(decideExcuse.approve(any(), eq(excuseId), eq(null)))
        .thenReturn(view(ExcuseStatus.APPROVED));

    mvc.perform(
            post("/api/v1/excuses/{id}/approve", excuseId)
                .with(token(UUID.randomUUID(), branchId, "admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
  }

  @Test
  void anAdminRejectsWithANoteAndADecidedExcuseIsAConflict() throws Exception {
    when(decideExcuse.reject(any(), eq(excuseId), eq("No proof")))
        .thenThrow(new DomainException(ErrorCode.EXCUSE_ALREADY_DECIDED));

    mvc.perform(
            post("/api/v1/excuses/{id}/reject", excuseId)
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"No proof\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("EXCUSE_ALREADY_DECIDED"));
    verify(decideExcuse).reject(any(), eq(excuseId), eq("No proof"));
  }

  @Test
  void studentsAndTeachersDoNotListExcuses() throws Exception {
    mvc.perform(get("/api/v1/excuses").with(token(UUID.randomUUID(), branchId, "student")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/excuses").with(token(UUID.randomUUID(), branchId, "teacher")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(listExcuses);
  }

  @Test
  void guardiansAndAdminsListExcusesByStatus() throws Exception {
    when(listExcuses.list(any(), eq(ExcuseStatus.PENDING), eq(null), any()))
        .thenReturn(Page.empty());

    mvc.perform(
            get("/api/v1/excuses")
                .param("status", "PENDING")
                .with(token(UUID.randomUUID(), branchId, "admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }
}
