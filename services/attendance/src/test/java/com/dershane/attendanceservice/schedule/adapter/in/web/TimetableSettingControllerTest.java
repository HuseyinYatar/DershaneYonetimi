package com.dershane.attendanceservice.schedule.adapter.in.web;

import static com.dershane.attendanceservice.support.TestJwts.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dershane.attendanceservice.schedule.application.port.in.GetTimetableSettingQuery;
import com.dershane.attendanceservice.schedule.application.port.in.SaveTimetableSettingCommand;
import com.dershane.attendanceservice.schedule.application.port.in.SaveTimetableSettingUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.TimetableSettingView;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.support.WebMvcTestSupport;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TimetableSettingController.class)
@Import({WebMvcTestSupport.class, TimetableSettingWebMapperImpl.class})
class TimetableSettingControllerTest {

  @Autowired private MockMvc mvc;
  @MockitoBean private GetTimetableSettingQuery getSetting;
  @MockitoBean private SaveTimetableSettingUseCase saveSetting;

  private final UUID branchId = UUID.randomUUID();

  private TimetableSettingView view(boolean customized) {
    return new TimetableSettingView(branchId, LocalTime.of(8, 10), 50, 10, 12, 0, customized);
  }

  private static final String BODY =
      "{\"firstLessonStart\":\"09:00\",\"lessonMinutes\":40,\"breakMinutes\":15,\"lessonCount\":8}";

  @Test
  void onlyAdminsReadOrWriteTheSetting() throws Exception {
    for (String role : new String[] {"student", "teacher", "guardian"}) {
      mvc.perform(get("/api/v1/timetable-setting").with(token(UUID.randomUUID(), branchId, role)))
          .andExpect(status().isForbidden());
      mvc.perform(
              put("/api/v1/timetable-setting")
                  .with(token(UUID.randomUUID(), branchId, role))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY))
          .andExpect(status().isForbidden());
    }
    verifyNoInteractions(getSetting, saveSetting);
  }

  @Test
  void anAdminReadsTheSetting() throws Exception {
    when(getSetting.get(any(), any())).thenReturn(view(false));

    mvc.perform(get("/api/v1/timetable-setting").with(token(UUID.randomUUID(), branchId, "admin")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.branchId").value(branchId.toString()))
        .andExpect(jsonPath("$.lessonMinutes").value(50))
        .andExpect(jsonPath("$.lessonCount").value(12))
        .andExpect(jsonPath("$.lunchLesson").value(0))
        .andExpect(jsonPath("$.customized").value(false));
  }

  @Test
  void anAdminSavesTheSetting() throws Exception {
    when(saveSetting.save(any(), any())).thenReturn(view(true));

    mvc.perform(
            put("/api/v1/timetable-setting")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customized").value(true));
  }

  @Test
  void theLunchLessonIsOptionalInTheRequestAndGoesThroughWhenGiven() throws Exception {
    when(saveSetting.save(any(), any())).thenReturn(view(true));

    mvc.perform(
            put("/api/v1/timetable-setting")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("}", ",\"lunchLesson\":5}")))
        .andExpect(status().isOk());

    ArgumentCaptor<SaveTimetableSettingCommand> command =
        ArgumentCaptor.forClass(SaveTimetableSettingCommand.class);
    verify(saveSetting).save(any(), command.capture());
    assertThat(command.getValue().lunchLesson()).isEqualTo(5);
  }

  @Test
  void withoutTheLunchLessonTheCommandMeansNoLunch() throws Exception {
    when(saveSetting.save(any(), any())).thenReturn(view(true));

    mvc.perform(
            put("/api/v1/timetable-setting")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk());

    ArgumentCaptor<SaveTimetableSettingCommand> command =
        ArgumentCaptor.forClass(SaveTimetableSettingCommand.class);
    verify(saveSetting).save(any(), command.capture());
    assertThat(command.getValue().lunchLesson()).isZero();
  }

  @Test
  void aMissingFieldIsABadRequest() throws Exception {
    mvc.perform(
            put("/api/v1/timetable-setting")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstLessonStart\":\"09:00\",\"lessonMinutes\":40}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    verifyNoInteractions(saveSetting);
  }

  @Test
  void aRuleViolationIsAnUnprocessableEntityWithTheTurkishText() throws Exception {
    when(saveSetting.save(any(), any()))
        .thenThrow(new DomainException(ErrorCode.TIMETABLE_SETTING_INVALID));

    mvc.perform(
            put("/api/v1/timetable-setting")
                .with(token(UUID.randomUUID(), branchId, "admin"))
                .header("Accept-Language", "tr")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.errorCode").value("TIMETABLE_SETTING_INVALID"))
        .andExpect(jsonPath("$.detail").value("Ders saati ayarı geçersiz."));
  }
}
