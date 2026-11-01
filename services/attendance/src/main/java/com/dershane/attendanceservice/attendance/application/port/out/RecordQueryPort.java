package com.dershane.attendanceservice.attendance.application.port.out;

import com.dershane.attendanceservice.attendance.application.port.in.AttendanceRecordView;
import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RecordQueryPort {

  /** Newest session first. */
  Page<AttendanceRecordView> findViews(RecordFilter filter, Pageable pageable);

  Optional<AttendanceRecordView> findViewById(UUID recordId);

  /** Number of matching records per status; statuses without records are absent. */
  Map<AttendanceStatus, Long> countByStatus(RecordFilter filter);
}
