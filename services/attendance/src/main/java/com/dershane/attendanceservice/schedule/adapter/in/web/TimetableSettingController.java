package com.dershane.attendanceservice.schedule.adapter.in.web;

import com.dershane.attendanceservice.schedule.adapter.in.web.dto.SaveTimetableSettingRequest;
import com.dershane.attendanceservice.schedule.adapter.in.web.dto.TimetableSettingResponse;
import com.dershane.attendanceservice.schedule.application.port.in.GetTimetableSettingQuery;
import com.dershane.attendanceservice.schedule.application.port.in.SaveTimetableSettingUseCase;
import com.dershane.attendanceservice.shared.adapter.in.security.SecurityRoles;
import com.dershane.attendanceservice.shared.adapter.in.web.CurrentActor;
import com.dershane.attendanceservice.shared.application.Actor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Timetable setting")
@RestController
@RequestMapping("/api/v1/timetable-setting")
@RequiredArgsConstructor
@PreAuthorize(SecurityRoles.ANY_ADMIN)
class TimetableSettingController {

  private final GetTimetableSettingQuery getSetting;
  private final SaveTimetableSettingUseCase saveSetting;
  private final TimetableSettingWebMapper mapper;

  @Operation(summary = "The branch's lesson-hour grid (defaults when never customized)")
  @GetMapping
  public TimetableSettingResponse get(
      @CurrentActor Actor actor, @RequestParam(required = false) UUID branchId) {
    return mapper.toResponse(getSetting.get(actor, branchId));
  }

  @Operation(summary = "Set the branch's lesson-hour grid; existing schedules are not rewritten")
  @PutMapping
  public TimetableSettingResponse save(
      @CurrentActor Actor actor, @Valid @RequestBody SaveTimetableSettingRequest request) {
    return mapper.toResponse(saveSetting.save(actor, mapper.toCommand(request)));
  }
}
