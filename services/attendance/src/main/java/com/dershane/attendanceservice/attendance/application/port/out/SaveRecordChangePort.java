package com.dershane.attendanceservice.attendance.application.port.out;

import com.dershane.attendanceservice.attendance.domain.AttendanceRecordChange;
import java.util.List;

public interface SaveRecordChangePort {

  /** Appends audit lines; they are never updated or deleted. */
  void append(List<AttendanceRecordChange> changes);
}
