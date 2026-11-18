package com.dershane.attendanceservice.schedule.adapter.in.web;

import static com.dershane.attendanceservice.support.TestJwts.token;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.attendanceservice.schedule.application.port.in.CommitTimetableDraftUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.DiscardTimetableDraftUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.GenerateTimetableDraftUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.GetTimetableDraftQuery;
import com.dershane.attendanceservice.schedule.application.port.in.MoveDraftLessonUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.RemoveDraftLessonUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.TimetableDraftView;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.support.WebMvcTestSupport;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TimetableDraftController.class)
@Import({WebMvcTestSupport.class, TimetableDraftWebMapperImpl.class})
class TimetableDraftControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private GenerateTimetableDraftUseCase generate;
  @MockitoBean private GetTimetableDraftQuery getDraft;
  @MockitoBean private MoveDraftLessonUseCase moveLesson;
  @MockitoBean private RemoveDraftLessonUseCase removeLesson;
  @MockitoBean private DiscardTimetableDraftUseCase discard;
  @MockitoBean private CommitTimetableDraftUseCase commit;

  private final UUID branchId = UUID.randomUUID();
  private final UUID lessonId = UUID.randomUUID();

  private TimetableDraftView draft() {
    return new TimetableDraftView(
        UUID.randomUUID(),
        branchId,
        Instant.parse("2026-11-18T10:00:00Z"),
        List.of(
            new TimetableDraftView.Lesson(
                lessonId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                DayOfWeek.MONDAY,
                LocalTime.of(8, 10),
                LocalTime.of(9, 0))),
        List.of(
            new TimetableDraftView.Missing(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2)));
  }

  @Test
  void onlyAdminsUseTheDraft() throws Exception {
    for (String role : new String[] {"student", "teacher", "guardian"}) {
      mvc.perform(post("/api/v1/timetable-drafts").with(token(UUID.randomUUID(), branchId, role)))
          .andExpect(status().isForbidden());
      mvc.perform(
              get("/api/v1/timetable-drafts/current")
                  .with(token(UUID.randomUUID(), branchId, role)))
          .andExpect(status().isForbidden());
      mvc.perform(
              delete("/api/v1/timetable-drafts/current")
                  .with(token(UUID.randomUUID(), branchId, role)))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(generate, getDraft, moveLesson, removeLesson, discard, commit);
  }

  @Test
  void generatingReturns201WithTimesWrittenAsHoursAndMinutes() throws Exception {
    when(generate.generate(any(), any())).thenReturn(draft());

    mvc.perform(post("/api/v1/timetable-drafts").with(token(UUID.randomUUID(), branchId, "admin")))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/timetable-drafts/current"))
        .andExpect(jsonPath("$.branchId").value(branchId.toString()))
        .andExpect(jsonPath("$.lessons[0].dayOfWeek").value("MONDAY"))
        .andExpect(jsonPath("$.lessons[0].startsAt").value("08:10"))
        .andExpect(jsonPath("$.lessons[0].endsAt").value("09:00"))
        .andExpect(jsonPath("$.missing[0].missingHours").value(2));
  }

  @Test
  void generatingAcceptsTheDaysAndABranch() throws Exception {
    when(generate.generate(any(), any())).thenReturn(draft());

    mvc.perform(
            post("/api/v1/timetable-drafts")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"branchId\":\"" + branchId + "\",\"days\":[\"MONDAY\",\"SATURDAY\"]}"))
        .andExpect(status().isCreated());
  }

  @Test
  void aMissingDraftIsA404ProblemInTurkish() throws Exception {
    when(getDraft.current(any(), any()))
        .thenThrow(new DomainException(ErrorCode.TIMETABLE_DRAFT_NOT_FOUND));

    mvc.perform(
            get("/api/v1/timetable-drafts/current")
                .with(token(UUID.randomUUID(), branchId, "admin")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("TIMETABLE_DRAFT_NOT_FOUND"))
        .andExpect(jsonPath("$.detail").value("Bu şube için taslak program yok."));
  }

  @Test
  void movingALessonReturnsItAndAClashIsAConflict() throws Exception {
    when(moveLesson.move(any(), any()))
        .thenReturn(draft().lessons().get(0))
        .thenThrow(new DomainException(ErrorCode.TEACHER_BUSY));
    String body = "{\"dayOfWeek\":\"MONDAY\",\"startsAt\":\"08:10\",\"endsAt\":\"09:00\"}";

    mvc.perform(
            put("/api/v1/timetable-drafts/current/lessons/{id}", lessonId)
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(lessonId.toString()))
        .andExpect(jsonPath("$.startsAt").value("08:10"));
    mvc.perform(
            put("/api/v1/timetable-drafts/current/lessons/{id}", lessonId)
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errorCode").value("TEACHER_BUSY"));
  }

  @Test
  void movingNeedsTheDayAndBothTimes() throws Exception {
    mvc.perform(
            put("/api/v1/timetable-drafts/current/lessons/{id}", lessonId)
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dayOfWeek\":\"MONDAY\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    verifyNoInteractions(moveLesson);
  }

  @Test
  void removingAndDiscardingAnswer204() throws Exception {
    mvc.perform(
            delete("/api/v1/timetable-drafts/current/lessons/{id}", lessonId)
                .param("branchId", branchId.toString())
                .with(token(UUID.randomUUID(), branchId, "admin")))
        .andExpect(status().isNoContent());
    mvc.perform(
            delete("/api/v1/timetable-drafts/current")
                .with(token(UUID.randomUUID(), branchId, "admin")))
        .andExpect(status().isNoContent());

    verify(removeLesson).remove(any(), eq(branchId), eq(lessonId));
  }

  @Test
  void committingReturnsHowManySchedulesWereCreated() throws Exception {
    when(commit.commit(any(), any(), eq(LocalDate.of(2026, 11, 23)))).thenReturn(7);

    mvc.perform(
            post("/api/v1/timetable-drafts/current/commit")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"validFrom\":\"2026-11-23\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(7));
  }

  @Test
  void committingNeedsAStartDate() throws Exception {
    mvc.perform(
            post("/api/v1/timetable-drafts/current/commit")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(commit);
  }

  @Test
  void anUnavailableUserServiceIsA503() throws Exception {
    when(generate.generate(any(), any()))
        .thenThrow(new DomainException(ErrorCode.USER_SERVICE_UNAVAILABLE));

    mvc.perform(post("/api/v1/timetable-drafts").with(token(UUID.randomUUID(), branchId, "admin")))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.errorCode").value("USER_SERVICE_UNAVAILABLE"));
  }
}
