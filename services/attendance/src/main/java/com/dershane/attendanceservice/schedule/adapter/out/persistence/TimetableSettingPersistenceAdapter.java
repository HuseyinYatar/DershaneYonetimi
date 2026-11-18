package com.dershane.attendanceservice.schedule.adapter.out.persistence;

import com.dershane.attendanceservice.schedule.application.port.out.LoadTimetableSettingPort;
import com.dershane.attendanceservice.schedule.application.port.out.SaveTimetableSettingPort;
import com.dershane.attendanceservice.schedule.domain.TimetableSetting;
import com.dershane.attendanceservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class TimetableSettingPersistenceAdapter
    implements LoadTimetableSettingPort, SaveTimetableSettingPort {

  private final TimetableSettingJpaRepository repository;
  private final TimetableSettingPersistenceMapper mapper;

  @Override
  public Optional<TimetableSetting> findByBranchId(UUID branchId) {
    return repository.findById(branchId).map(mapper::toDomain);
  }

  @Override
  public TimetableSetting save(TimetableSetting setting) {
    if (setting.getVersion() == null) {
      return mapper.toDomain(repository.save(mapper.toEntity(setting)));
    }
    TimetableSettingJpaEntity entity =
        repository
            .findById(setting.getBranchId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.DATA_CONFLICT,
                        "Timetable setting to save no longer exists",
                        "branchId",
                        setting.getBranchId()));
    VersionChecks.requireCurrent(
        log, "TimetableSetting", setting.getBranchId(), setting.getVersion(), entity.getVersion());
    mapper.update(entity, setting);
    return mapper.toDomain(entity);
  }
}
