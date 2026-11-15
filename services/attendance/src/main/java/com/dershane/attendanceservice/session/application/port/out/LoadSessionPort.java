package com.dershane.attendanceservice.session.application.port.out;

import com.dershane.attendanceservice.session.domain.ClassSession;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoadSessionPort {

  Optional<ClassSession> findById(UUID sessionId);

  boolean existsByScheduleIdAndSessionDate(UUID scheduleId, LocalDate sessionDate);

  /** A planned or completed session of the classroom overlaps the time range. */
  boolean existsClassroomOverlap(
      UUID branchId,
      UUID classroomId,
      LocalDate date,
      LocalTime startsAt,
      LocalTime endsAt,
      UUID excludeSessionId);

  /** A planned or completed session of the teacher overlaps the time range. */
  boolean existsTeacherOverlap(
      UUID branchId,
      UUID teacherId,
      LocalDate date,
      LocalTime startsAt,
      LocalTime endsAt,
      UUID excludeSessionId);

  /** Planned sessions on days before {@code date}. */
  List<ClassSession> findPlannedBefore(LocalDate date);
}
