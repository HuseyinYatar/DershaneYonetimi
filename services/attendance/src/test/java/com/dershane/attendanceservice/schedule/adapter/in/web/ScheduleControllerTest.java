package com.dershane.attendanceservice.schedule.adapter.in.web;

import static com.dershane.attendanceservice.support.TestJwts.token;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.attendanceservice.schedule.application.port.in.CreateScheduleUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.DeactivateScheduleUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.GetScheduleQuery;
import com.dershane.attendanceservice.schedule.application.port.in.ListSchedulesQuery;
import com.dershane.attendanceservice.schedule.application.port.in.UpdateScheduleUseCase;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.support.WebMvcTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ScheduleController.class)
@Import({WebMvcTestSupport.class, ScheduleWebMapperImpl.class})
class ScheduleControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private CreateScheduleUseCase createSchedule;
  @MockitoBean private UpdateScheduleUseCase updateSchedule;
  @MockitoBean private DeactivateScheduleUseCase deactivateSchedule;
  @MockitoBean private GetScheduleQuery getSchedule;
  @MockitoBean private ListSchedulesQuery listSchedules;

  private final UUID branchId = UUID.randomUUID();

  private String createBody() {
    return "{\"branchId\":\"%s\",\"classroomId\":\"%s\",\"teacherId\":\"%s\",\"subjectId\":\"%s\","
            .formatted(branchId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
        + "\"dayOfWeek\":\"MONDAY\",\"startsAt\":\"09:10\",\"endsAt\":\"10:00\","
        + "\"validFrom\":\"2026-11-23\"}";
  }

  @Test
  void aClassroomClashIsAConflictWithATurkishMessage() throws Exception {
    when(createSchedule.create(any(), any()))
        .thenThrow(new DomainException(ErrorCode.CLASSROOM_BUSY));

    mvc.perform(
            post("/api/v1/class-schedules")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("CLASSROOM_BUSY"))
        .andExpect(jsonPath("$.detail").value("Sınıfın bu saatte başka bir dersi var."));
  }

  @Test
  void aTeacherClashOnUpdateIsAConflictToo() throws Exception {
    when(updateSchedule.update(any(), any(), any()))
        .thenThrow(new DomainException(ErrorCode.TEACHER_BUSY));
    String body =
        "{\"teacherId\":\"%s\",\"subjectId\":\"%s\",\"dayOfWeek\":\"MONDAY\","
                .formatted(UUID.randomUUID(), UUID.randomUUID())
            + "\"startsAt\":\"09:10\",\"endsAt\":\"10:00\",\"validFrom\":\"2026-11-23\"}";

    mvc.perform(
            put("/api/v1/class-schedules/{id}", UUID.randomUUID())
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("TEACHER_BUSY"))
        .andExpect(jsonPath("$.detail").value("Öğretmenin bu saatte başka bir dersi var."));
  }

  @Test
  void theLunchHourIsAnUnprocessableRequest() throws Exception {
    when(createSchedule.create(any(), any()))
        .thenThrow(new DomainException(ErrorCode.LUNCH_BREAK_SLOT));

    mvc.perform(
            post("/api/v1/class-schedules")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody()))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.errorCode").value("LUNCH_BREAK_SLOT"));
  }
}
