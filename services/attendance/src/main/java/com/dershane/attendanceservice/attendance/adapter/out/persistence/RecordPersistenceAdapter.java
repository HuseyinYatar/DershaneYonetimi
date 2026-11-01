package com.dershane.attendanceservice.attendance.adapter.out.persistence;

import com.dershane.attendanceservice.attendance.application.port.out.LoadRecordPort;
import com.dershane.attendanceservice.attendance.application.port.out.SaveRecordChangePort;
import com.dershane.attendanceservice.attendance.application.port.out.SaveRecordPort;
import com.dershane.attendanceservice.attendance.domain.AttendanceRecord;
import com.dershane.attendanceservice.attendance.domain.AttendanceRecordChange;
import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import com.dershane.attendanceservice.session.domain.SessionStatus;
import com.dershane.attendanceservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class RecordPersistenceAdapter
    implements LoadRecordPort, SaveRecordPort, SaveRecordChangePort {

  private final AttendanceRecordJpaRepository records;
  private final AttendanceRecordChangeJpaRepository changes;
  private final AttendancePersistenceMapper mapper;

  @Override
  public List<AttendanceRecord> findBySessionId(UUID sessionId) {
    return records.findBySessionId(sessionId).stream().map(mapper::toDomain).toList();
  }

  @Override
  public Optional<AttendanceRecord> findBySessionIdAndStudentId(UUID sessionId, UUID studentId) {
    return records.findBySessionIdAndStudentId(sessionId, studentId).map(mapper::toDomain);
  }

  @Override
  public List<AttendanceRecord> findAbsencesInCompletedSessions(
      UUID studentId, LocalDate fromDate, LocalDate toDate) {
    return records
        .findByStatusInSessionsBetween(
            studentId, AttendanceStatus.ABSENT, fromDate, toDate, SessionStatus.COMPLETED)
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public List<AttendanceRecord> saveAll(List<AttendanceRecord> toSave) {
    List<AttendanceRecordJpaEntity> entities = new ArrayList<>(toSave.size());
    for (AttendanceRecord record : toSave) {
      if (record.getVersion() == null) {
        entities.add(records.save(mapper.toEntity(record)));
        continue;
      }
      AttendanceRecordJpaEntity entity =
          records
              .findById(record.getId())
              .orElseThrow(
                  () ->
                      Rejections.reject(
                          log,
                          ErrorCode.RESOURCE_NOT_FOUND,
                          "Attendance record to save no longer exists",
                          "recordId",
                          record.getId()));
      VersionChecks.requireCurrent(
          log, "AttendanceRecord", record.getId(), record.getVersion(), entity.getVersion());
      mapper.update(entity, record);
      entities.add(entity);
    }
    // Writes now, so a duplicate submission fails here (uk_attendance_record_session_student).
    records.flush();
    return entities.stream().map(mapper::toDomain).toList();
  }

  @Override
  public void append(List<AttendanceRecordChange> toAppend) {
    changes.saveAll(toAppend.stream().map(mapper::toEntity).toList());
  }
}
