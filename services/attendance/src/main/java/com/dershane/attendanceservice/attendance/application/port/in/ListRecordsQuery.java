package com.dershane.attendanceservice.attendance.application.port.in;

import com.dershane.attendanceservice.shared.application.Actor;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListRecordsQuery {

  /**
   * A student sees their own records, a guardian their child's (a student id is required), a
   * teacher those of the sessions they teach, a branch admin those of their branch and a super
   * admin all. Null filters are not applied; {@code from} and {@code to} are inclusive.
   */
  Page<AttendanceRecordView> list(
      Actor actor,
      UUID studentId,
      UUID classroomId,
      LocalDate from,
      LocalDate to,
      Pageable pageable);
}
