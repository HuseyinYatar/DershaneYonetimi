package com.dershane.attendanceservice.schedule.application.service;

import static com.dershane.attendanceservice.support.TestJwts.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.attendanceservice.schedule.application.port.in.SaveTimetableSettingCommand;
import com.dershane.attendanceservice.schedule.application.port.in.TimetableSettingView;
import com.dershane.attendanceservice.schedule.application.port.out.LoadTimetableSettingPort;
import com.dershane.attendanceservice.schedule.application.port.out.SaveTimetableSettingPort;
import com.dershane.attendanceservice.schedule.domain.TimetableSetting;
import com.dershane.attendanceservice.shared.application.Actor;
import com.dershane.attendanceservice.shared.application.BranchAccessPolicy;
import com.dershane.attendanceservice.shared.domain.DomainException;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TimetableSettingServiceTest {

  @Mock private LoadTimetableSettingPort loadSetting;
  @Mock private SaveTimetableSettingPort saveSetting;

  private final UUID branchId = UUID.randomUUID();
  private final Actor admin = actor(UUID.randomUUID(), branchId, "ADMIN");

  private TimetableSettingService service;

  @BeforeEach
  void setUp() {
    service = new TimetableSettingService(loadSetting, saveSetting, new BranchAccessPolicy());
  }

  private SaveTimetableSettingCommand command(UUID branch) {
    return new SaveTimetableSettingCommand(branch, LocalTime.of(9, 0), 40, 15, 8, 5);
  }

  @Test
  void aBranchThatNeverCustomizedGetsTheDefaults() {
    when(loadSetting.findByBranchId(branchId)).thenReturn(Optional.empty());

    TimetableSettingView view = service.get(admin, null);

    assertThat(view.customized()).isFalse();
    assertThat(view.branchId()).isEqualTo(branchId);
    assertThat(view.firstLessonStart()).isEqualTo(LocalTime.of(8, 10));
    assertThat(view.lessonMinutes()).isEqualTo(50);
    assertThat(view.lessonCount()).isEqualTo(12);
    assertThat(view.lunchLesson()).isZero();
  }

  @Test
  void aSavedSettingIsReturnedAsCustomized() {
    when(loadSetting.findByBranchId(branchId))
        .thenReturn(
            Optional.of(new TimetableSetting(branchId, 2L, LocalTime.of(9, 0), 40, 15, 8, 5)));

    TimetableSettingView view = service.get(admin, branchId);

    assertThat(view.customized()).isTrue();
    assertThat(view.lessonMinutes()).isEqualTo(40);
    assertThat(view.lunchLesson()).isEqualTo(5);
  }

  @Test
  void savingForTheFirstTimeInsertsWithoutAVersion() {
    when(loadSetting.findByBranchId(branchId)).thenReturn(Optional.empty());
    when(saveSetting.save(any())).thenAnswer(call -> call.getArgument(0));

    TimetableSettingView view = service.save(admin, command(null));

    ArgumentCaptor<TimetableSetting> saved = ArgumentCaptor.forClass(TimetableSetting.class);
    verify(saveSetting).save(saved.capture());
    assertThat(saved.getValue().getBranchId()).isEqualTo(branchId);
    assertThat(saved.getValue().getVersion()).isNull();
    assertThat(view.customized()).isTrue();
    assertThat(view.lessonCount()).isEqualTo(8);
    assertThat(view.lunchLesson()).isEqualTo(5);
  }

  @Test
  void savingAgainCarriesTheLoadedVersionForOptimisticLocking() {
    when(loadSetting.findByBranchId(branchId))
        .thenReturn(
            Optional.of(TimetableSetting.of(branchId, 5L, LocalTime.of(8, 10), 50, 10, 12, 0)));
    when(saveSetting.save(any())).thenAnswer(call -> call.getArgument(0));

    service.save(admin, command(branchId));

    ArgumentCaptor<TimetableSetting> saved = ArgumentCaptor.forClass(TimetableSetting.class);
    verify(saveSetting).save(saved.capture());
    assertThat(saved.getValue().getVersion()).isEqualTo(5L);
  }

  @Test
  void anInvalidSettingIsNotSaved() {
    when(loadSetting.findByBranchId(branchId)).thenReturn(Optional.empty());

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(
            () ->
                service.save(
                    admin,
                    new SaveTimetableSettingCommand(branchId, LocalTime.of(9, 0), 10, 10, 8, 0)))
        .satisfies(
            ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.TIMETABLE_SETTING_INVALID));
    verify(saveSetting, never()).save(any());
  }

  @Test
  void anAdminOfAnotherBranchIsRejectedForReadAndWrite() {
    UUID other = UUID.randomUUID();

    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> service.get(admin, other))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION));
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> service.save(admin, command(other)))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BRANCH_SCOPE_VIOLATION));
    verify(saveSetting, never()).save(any());
  }

  @Test
  void aSuperAdminSavesForAnyBranchButMustNameOne() {
    UUID other = UUID.randomUUID();
    Actor superAdmin = actor(UUID.randomUUID(), branchId, "SUPER_ADMIN");
    Actor noBranch = new Actor(UUID.randomUUID(), java.util.Set.of("SUPER_ADMIN"), null);
    when(loadSetting.findByBranchId(other)).thenReturn(Optional.empty());
    when(saveSetting.save(any())).thenAnswer(call -> call.getArgument(0));

    assertThat(service.save(superAdmin, command(other)).branchId()).isEqualTo(other);
    assertThatExceptionOfType(DomainException.class)
        .isThrownBy(() -> service.get(noBranch, null))
        .satisfies(ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
  }
}
