package com.dershane.attendanceservice.session.adapter.out.persistence;

import com.dershane.attendanceservice.session.application.port.in.SessionView;
import com.dershane.attendanceservice.session.application.port.out.LoadSessionPort;
import com.dershane.attendanceservice.session.application.port.out.SaveSessionPort;
import com.dershane.attendanceservice.session.application.port.out.SessionQueryPort;
import com.dershane.attendanceservice.session.domain.ClassSession;
import com.dershane.attendanceservice.session.domain.SessionStatus;
import com.dershane.attendanceservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.attendanceservice.shared.domain.ErrorCode;
import com.dershane.attendanceservice.shared.domain.Rejections;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class SessionPersistenceAdapter
    implements LoadSessionPort, SaveSessionPort, SessionQueryPort {

  /** Cancelled and expired sessions take no time slot. */
  private static final Set<SessionStatus> OCCUPYING =
      Set.of(SessionStatus.PLANNED, SessionStatus.COMPLETED);

  /** The id no session has; stands in for "exclude nothing" because a null parameter is untyped. */
  private static final UUID NO_SESSION = new UUID(0L, 0L);

  private final ClassSessionJpaRepository repository;
  private final SessionPersistenceMapper mapper;

  @Override
  public Optional<ClassSession> findById(UUID sessionId) {
    return repository.findById(sessionId).map(mapper::toDomain);
  }

  @Override
  public boolean existsByScheduleIdAndSessionDate(UUID scheduleId, LocalDate sessionDate) {
    return repository.existsByScheduleIdAndSessionDate(scheduleId, sessionDate);
  }

  @Override
  public boolean existsClassroomOverlap(
      UUID classroomId,
      LocalDate date,
      LocalTime startsAt,
      LocalTime endsAt,
      UUID excludeSessionId) {
    return repository.existsClassroomOverlap(
        classroomId, date, startsAt, endsAt, OCCUPYING, orNone(excludeSessionId));
  }

  @Override
  public boolean existsTeacherOverlap(
      UUID teacherId, LocalDate date, LocalTime startsAt, LocalTime endsAt, UUID excludeSessionId) {
    return repository.existsTeacherOverlap(
        teacherId, date, startsAt, endsAt, OCCUPYING, orNone(excludeSessionId));
  }

  @Override
  public List<ClassSession> findPlannedBefore(LocalDate date) {
    return repository.findByStatusAndSessionDateBefore(SessionStatus.PLANNED, date).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public ClassSession save(ClassSession session) {
    if (session.getVersion() == null) {
      return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(session)));
    }
    ClassSessionJpaEntity entity =
        repository
            .findById(session.getId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.SESSION_NOT_FOUND,
                        "Session to save no longer exists",
                        "sessionId",
                        session.getId()));
    VersionChecks.requireCurrent(
        log, "ClassSession", session.getId(), session.getVersion(), entity.getVersion());
    mapper.update(entity, session);
    return mapper.toDomain(entity);
  }

  @Override
  public Optional<SessionView> findViewById(UUID sessionId) {
    return repository.findById(sessionId).map(mapper::toView);
  }

  @Override
  public Page<SessionView> findViews(
      UUID branchId,
      UUID classroomId,
      UUID teacherId,
      LocalDate from,
      LocalDate to,
      SessionStatus status,
      Pageable pageable) {
    return repository
        .findAll(
            SessionSpecifications.matching(branchId, classroomId, teacherId, from, to, status),
            pageable)
        .map(mapper::toView);
  }

  private static UUID orNone(UUID sessionId) {
    return sessionId == null ? NO_SESSION : sessionId;
  }
}
