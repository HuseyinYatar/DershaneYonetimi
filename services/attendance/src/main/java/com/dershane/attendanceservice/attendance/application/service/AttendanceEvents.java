package com.dershane.attendanceservice.attendance.application.service;

import com.dershane.attendanceservice.attendance.domain.AttendanceRecord;
import com.dershane.attendanceservice.session.domain.ClassSession;
import com.dershane.attendanceservice.shared.application.MetricNames;
import com.dershane.attendanceservice.shared.application.event.IntegrationEvent.StudentAbsent;
import com.dershane.attendanceservice.shared.application.event.IntegrationEvent.StudentExcusedByTeacher;
import com.dershane.attendanceservice.shared.application.port.out.IntegrationEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Decides which integration event a record's final state calls for and hands it to the outbox. Must
 * run inside the transaction that saved the record.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AttendanceEvents {

  private final IntegrationEventPublisher publisher;
  private final MeterRegistry meters;

  void publishFor(AttendanceRecord record, ClassSession session) {
    switch (record.notificationKind()) {
      case ABSENCE -> {
        publisher.publish(
            new StudentAbsent(
                record.getId(),
                session.getId(),
                record.getStudentId(),
                session.getClassroomId(),
                session.getBranchId(),
                session.getSubjectId(),
                session.getSessionDate(),
                session.getStartsAt(),
                session.getEndsAt()));
        meters.counter(MetricNames.ABSENCES_PUBLISHED).increment();
      }
      case TEACHER_EXCUSE -> {
        publisher.publish(
            new StudentExcusedByTeacher(
                record.getId(),
                session.getId(),
                record.getStudentId(),
                session.getClassroomId(),
                session.getBranchId(),
                session.getSubjectId(),
                session.getSessionDate(),
                session.getStartsAt(),
                session.getEndsAt()));
        meters.counter(MetricNames.EXCUSED_PUBLISHED).increment();
      }
      default ->
          log.atDebug()
              .addKeyValue("recordId", record.getId())
              .addKeyValue("status", record.getStatus())
              .log("Record status calls for no event");
    }
  }
}
