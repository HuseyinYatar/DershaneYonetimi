package com.dershane.attendanceservice.attendance.adapter.out.persistence;

import com.dershane.attendanceservice.attendance.domain.AttendanceStatus;
import com.dershane.attendanceservice.session.domain.SessionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * The absence query reads the session table by entity name. The module never touches the session
 * repository; the sub-select is read-side only.
 */
interface AttendanceRecordJpaRepository extends JpaRepository<AttendanceRecordJpaEntity, UUID> {

  List<AttendanceRecordJpaEntity> findBySessionId(UUID sessionId);

  Optional<AttendanceRecordJpaEntity> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);

  @Query(
      """
      select r from AttendanceRecordJpaEntity r
      where r.studentId = :studentId and r.status = :status
        and r.sessionId in (
          select s.id from ClassSessionJpaEntity s
          where s.sessionDate between :fromDate and :toDate and s.status = :sessionStatus)
      order by r.id
      """)
  List<AttendanceRecordJpaEntity> findByStatusInSessionsBetween(
      UUID studentId,
      AttendanceStatus status,
      LocalDate fromDate,
      LocalDate toDate,
      SessionStatus sessionStatus);
}
