package com.dershane.messagingservice.directory.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dershane.messagingservice.directory.application.port.in.RecordUserCommand;
import com.dershane.messagingservice.directory.application.port.out.UserReplicaPort;
import com.dershane.messagingservice.directory.domain.UserReplica;
import com.dershane.messagingservice.directory.domain.UserRole;
import com.dershane.messagingservice.shared.application.Actor;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserDirectoryServiceTest {

  @Mock private UserReplicaPort replicas;

  private final UUID userId = UUID.randomUUID();
  private final UUID branchId = UUID.randomUUID();

  private UserDirectoryService service() {
    return new UserDirectoryService(replicas);
  }

  private RecordUserCommand command(String role, long version) {
    return new RecordUserCommand(userId, role, branchId, "Ada", "Yilmaz", true, version);
  }

  private UserReplica stored(long version) {
    return new UserReplica(userId, 3L, UserRole.TEACHER, branchId, "Ada", "Yilmaz", true, version);
  }

  @Test
  void aNewStudentIsStored() {
    when(replicas.findById(userId)).thenReturn(Optional.empty());

    service().record(Actor.SYSTEM, command("STUDENT", 1));

    ArgumentCaptor<UserReplica> saved = ArgumentCaptor.forClass(UserReplica.class);
    verify(replicas).save(saved.capture());
    assertThat(saved.getValue().getRole()).isEqualTo(UserRole.STUDENT);
    assertThat(saved.getValue().getVersion()).isNull();
  }

  @Test
  void guardiansAndAdminsAreNotCopied() {
    service().record(Actor.SYSTEM, command("GUARDIAN", 1));
    service().record(Actor.SYSTEM, command("ADMIN", 1));

    verify(replicas, never()).save(any());
  }

  @Test
  void aNewerEventUpdatesTheStoredCopy() {
    when(replicas.findById(userId)).thenReturn(Optional.of(stored(2)));

    service().record(Actor.SYSTEM, command("TEACHER", 3));

    ArgumentCaptor<UserReplica> saved = ArgumentCaptor.forClass(UserReplica.class);
    verify(replicas).save(saved.capture());
    assertThat(saved.getValue().getRecordVersion()).isEqualTo(3);
    assertThat(saved.getValue().getVersion()).as("loaded version kept for locking").isEqualTo(3L);
  }

  @Test
  void staleAndRepeatedEventsAreIgnored() {
    when(replicas.findById(userId)).thenReturn(Optional.of(stored(5)));

    service().record(Actor.SYSTEM, command("TEACHER", 4));
    service().record(Actor.SYSTEM, command("TEACHER", 5));

    verify(replicas, never()).save(any());
  }
}
