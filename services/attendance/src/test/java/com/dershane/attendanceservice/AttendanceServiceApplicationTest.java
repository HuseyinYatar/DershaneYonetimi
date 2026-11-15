package com.dershane.attendanceservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AttendanceServiceApplicationTest {

  @Test
  void aJobRunSwitchesTheOutboxAndTheEventConsumerOffWithArguments() {
    String[] args = {"--attendance.job=generate-sessions"};

    assertThat(AttendanceServiceApplication.runArguments(args))
        .containsExactly(
            "--attendance.job=generate-sessions",
            "--attendance.outbox.enabled=false",
            "--attendance.inbox.enabled=false");
  }

  @Test
  void aNormalStartKeepsItsArgumentsUntouched() {
    String[] args = {"--server.port=9000"};

    assertThat(AttendanceServiceApplication.runArguments(args)).isSameAs(args);
    assertThat(AttendanceServiceApplication.isJobRun(args)).isFalse();
  }
}
