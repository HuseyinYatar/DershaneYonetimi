package com.dershane.taskservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TaskServiceApplicationTest {

  @Test
  void aJobRunSwitchesTheOutboxAndTheEventConsumerOffWithArguments() {
    String[] args = {"--task.job=check-deadlines"};

    assertThat(TaskServiceApplication.runArguments(args))
        .containsExactly(
            "--task.job=check-deadlines",
            "--task.outbox.enabled=false",
            "--task.inbox.enabled=false");
  }

  @Test
  void aNormalStartKeepsItsArgumentsUntouched() {
    String[] args = {"--server.port=9000"};

    assertThat(TaskServiceApplication.runArguments(args)).isSameAs(args);
    assertThat(TaskServiceApplication.isJobRun(args)).isFalse();
  }
}
