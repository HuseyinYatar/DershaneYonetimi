package com.dershane.attendanceservice.attendance.application.port.out;

import com.dershane.attendanceservice.attendance.domain.AttendanceRecord;
import java.util.List;

public interface SaveRecordPort {

  /**
   * Inserts new records or updates loaded ones and writes them to the database right away, so a
   * duplicate submission fails here and not at commit; returns them with ids and versions.
   */
  List<AttendanceRecord> saveAll(List<AttendanceRecord> records);
}
