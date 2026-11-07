package com.dershane.taskservice.assignment.adapter.in.event;

import com.dershane.taskservice.assignment.application.port.in.StudentJoinedClassroomUseCase;
import com.dershane.taskservice.assignment.application.port.in.StudentLeftClassroomUseCase;
import com.dershane.taskservice.roster.application.event.MembershipClosed;
import com.dershane.taskservice.roster.application.event.MembershipOpened;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Follows the roster copy: a student who joins a classroom gets its open tasks, one who leaves
 * loses the open ones. Handled inside the recording transaction, so the copy and the assignments
 * commit or roll back together (a failed event is retried by the queue).
 */
@Component
@RequiredArgsConstructor
class MembershipListener {

  private final StudentJoinedClassroomUseCase joined;
  private final StudentLeftClassroomUseCase left;

  @EventListener
  void onMembershipOpened(MembershipOpened event) {
    joined.studentJoined(event.studentId(), event.classroomId(), event.startDate());
  }

  @EventListener
  void onMembershipClosed(MembershipClosed event) {
    left.studentLeft(event.studentId(), event.classroomId());
  }
}
