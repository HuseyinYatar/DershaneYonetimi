package com.dershane.attendanceservice.attendance.adapter.in.event;

import com.dershane.attendanceservice.attendance.application.port.in.ApplyExcuseUseCase;
import com.dershane.attendanceservice.excuse.application.event.ExcuseApproved;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Excuses the student's recorded absences in the approved range. The event is published inside the
 * approval transaction and handled synchronously, so both changes commit or roll back together.
 */
@Component
@RequiredArgsConstructor
class ExcuseApprovedListener {

  private final ApplyExcuseUseCase applyExcuse;

  @EventListener
  void onExcuseApproved(ExcuseApproved event) {
    applyExcuse.apply(
        event.excuseId(), event.studentId(), event.fromDate(), event.toDate(), event.decidedBy());
  }
}
