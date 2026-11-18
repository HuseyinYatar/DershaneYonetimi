package com.dershane.attendanceservice.schedule.application.service;

import com.dershane.attendanceservice.schedule.application.port.in.GetTimetableSettingQuery;
import com.dershane.attendanceservice.schedule.application.port.in.SaveTimetableSettingCommand;
import com.dershane.attendanceservice.schedule.application.port.in.SaveTimetableSettingUseCase;
import com.dershane.attendanceservice.schedule.application.port.in.TimetableSettingView;
import com.dershane.attendanceservice.schedule.application.port.out.LoadTimetableSettingPort;
import com.dershane.attendanceservice.schedule.application.port.out.SaveTimetableSettingPort;
import com.dershane.attendanceservice.schedule.domain.TimetableSetting;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import io.micrometer.observation.annotation.Observed;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
class TimetableSettingService implements GetTimetableSettingQuery, SaveTimetableSettingUseCase {

  private final LoadTimetableSettingPort loadSetting;
  private final SaveTimetableSettingPort saveSetting;
  private final BranchAccessPolicy branchAccess;

  @Override
  @Transactional(readOnly = true)
  @Observed(name = "timetable-setting.get")
  public TimetableSettingView get(Actor actor, UUID branchId) {
    UUID branch = resolveBranch(actor, branchId, "timetable-setting.get");
    return loadSetting
        .findByBranchId(branch)
        .map(setting -> view(setting, true))
        .orElseGet(() -> view(TimetableSetting.defaults(branch), false));
  }

  @Override
  @Transactional
  @Observed(name = "timetable-setting.save")
  public TimetableSettingView save(Actor actor, SaveTimetableSettingCommand command) {
    UUID branch = resolveBranch(actor, command.branchId(), "timetable-setting.save");
    Long version =
        loadSetting.findByBranchId(branch).map(TimetableSetting::getVersion).orElse(null);
    TimetableSetting saved =
        saveSetting.save(
            TimetableSetting.of(
                branch,
                version,
                command.firstLessonStart(),
                command.lessonMinutes(),
                command.breakMinutes(),
                command.lessonCount(),
                command.lunchLesson()));
    log.atInfo()
        .addKeyValue("branchId", branch)
        .addKeyValue("lessonCount", saved.getLessonCount())
        .log("Timetable setting saved");
    return view(saved, true);
  }

  private UUID resolveBranch(Actor actor, UUID requested, String action) {
    return BranchResolution.resolve(branchAccess, actor, requested, action);
  }

  private static TimetableSettingView view(TimetableSetting setting, boolean customized) {
    return new TimetableSettingView(
        setting.getBranchId(),
        setting.getFirstLessonStart(),
        setting.getLessonMinutes(),
        setting.getBreakMinutes(),
        setting.getLessonCount(),
        setting.getLunchLesson(),
        customized);
  }
}
