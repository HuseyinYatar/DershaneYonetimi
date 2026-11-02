package com.dershane.attendanceservice.attendance.adapter.in.web;

import static com.dershane.attendanceservice.support.TestJwts.token;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.attendanceservice.attendance.application.port.in.AttendanceSummaryQuery;
import com.dershane.attendanceservice.attendance.application.port.in.AttendanceSummaryView;
import com.dershane.attendanceservice.attendance.application.port.in.ListRecordsQuery;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.support.WebMvcTestSupport;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecordController.class)
@Import({WebMvcTestSupport.class, AttendanceWebMapperImpl.class})
class RecordControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private ListRecordsQuery listRecords;
  @MockitoBean private AttendanceSummaryQuery attendanceSummary;

  private final UUID studentId = UUID.randomUUID();
  private final UUID branchId = UUID.randomUUID();

  @Test
  void requestWithoutTokenGets401() throws Exception {
    mvc.perform(get("/api/v1/attendance-records"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("AUTHENTICATION_REQUIRED"));
    verifyNoInteractions(listRecords);
  }

  @Test
  void dateFiltersAreParsedAsIsoDates() throws Exception {
    when(listRecords.list(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

    mvc.perform(
            get("/api/v1/attendance-records")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .with(token(studentId, branchId, "student")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());

    org.mockito.Mockito.verify(listRecords)
        .list(
            any(),
            eq(null),
            eq(null),
            eq(LocalDate.of(2026, 10, 1)),
            eq(LocalDate.of(2026, 10, 31)),
            any());
  }

  @Test
  void aGuardianAskingForAStrangerGetsForbidden() throws Exception {
    when(listRecords.list(any(), any(), any(), any(), any(), any()))
        .thenThrow(new DomainException(ErrorCode.GUARDIAN_NOT_LINKED));

    mvc.perform(
            get("/api/v1/attendance-records")
                .param("studentId", studentId.toString())
                .with(token(UUID.randomUUID(), branchId, "guardian")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("GUARDIAN_NOT_LINKED"))
        .andExpect(jsonPath("$.detail").value("Bu öğrenci hesabınıza bağlı değil."));
  }

  @Test
  void theSummaryReturnsCountsPerStatus() throws Exception {
    when(attendanceSummary.summarize(any(), eq(studentId), any(), any()))
        .thenReturn(new AttendanceSummaryView(studentId, null, null, 18, 2, 1, 1, 22));

    mvc.perform(
            get("/api/v1/attendance-summaries")
                .param("studentId", studentId.toString())
                .with(token(UUID.randomUUID(), branchId, "teacher")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.present").value(18))
        .andExpect(jsonPath("$.absent").value(2))
        .andExpect(jsonPath("$.late").value(1))
        .andExpect(jsonPath("$.excused").value(1))
        .andExpect(jsonPath("$.total").value(22));
  }
}
