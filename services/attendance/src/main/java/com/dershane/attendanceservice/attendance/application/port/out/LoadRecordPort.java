package com.dershane.attendanceservice.attendance.application.port.out;

import com.dershane.attendanceservice.attendance.domain.AttendanceRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadRecordPort {

  List<AttendanceRecord> findBySessionId(UUID sessionId);

  Optional<AttendanceRecord> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);

  /** The student's {@code ABSENT} records in completed sessions held between the dates. */
  List<AttendanceRecord> findAbsencesInCompletedSessions(
      UUID studentId, LocalDate fromDate, LocalDate toDate);
}
