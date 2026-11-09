package com.dershane.messagingservice.directory.application.service;

import com.dershane.messagingservice.directory.application.port.in.DirectoryEntryView;
import com.dershane.messagingservice.directory.application.port.in.DirectoryRole;
import com.dershane.messagingservice.directory.application.port.in.RecordUserCommand;
import com.dershane.messagingservice.directory.application.port.in.RecordUserUseCase;
import com.dershane.messagingservice.directory.application.port.in.UserDirectoryQuery;
import com.dershane.messagingservice.directory.application.port.out.UserReplicaPort;
import com.dershane.messagingservice.directory.domain.UserReplica;
import com.dershane.messagingservice.directory.domain.UserRole;
import com.dershane.messagingservice.shared.application.Actor;
import io.micrometer.observation.annotation.Observed;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Keeps the local copy of students and teachers; every operation is idempotent. */
@Slf4j
@Service
@RequiredArgsConstructor
class UserDirectoryService implements RecordUserUseCase, UserDirectoryQuery {

  private final UserReplicaPort replicas;

  @Override
  @Transactional
  @Observed(name = "directory.user.record")
  public void record(Actor actor, RecordUserCommand command) {
    Optional<UserRole> role = UserRole.parse(command.role());
    if (role.isEmpty()) {
      log.atDebug()
          .addKeyValue("userId", command.userId())
          .addKeyValue("role", command.role())
          .log("User is neither student nor teacher; not copied");
      return;
    }
    Optional<UserReplica> existing = replicas.findById(command.userId());
    if (existing.isPresent() && !existing.get().isOlderThan(command.recordVersion())) {
      log.atInfo()
          .addKeyValue("userId", command.userId())
          .addKeyValue("storedVersion", existing.get().getRecordVersion())
          .addKeyValue("eventVersion", command.recordVersion())
          .log("Stale or repeated user event ignored");
      return;
    }
    replicas.save(
        new UserReplica(
            command.userId(),
            existing.map(UserReplica::getVersion).orElse(null),
            role.get(),
            command.branchId(),
            command.firstName(),
            command.lastName(),
            command.active(),
            command.recordVersion()));
    log.atInfo()
        .addKeyValue("userId", command.userId())
        .addKeyValue("recordVersion", command.recordVersion())
        .log("User copy recorded");
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<DirectoryEntryView> find(UUID userId) {
    return replicas
        .findById(userId)
        .map(
            r ->
                new DirectoryEntryView(
                    r.getUserId(),
                    DirectoryRole.valueOf(r.getRole().name()),
                    r.getBranchId(),
                    r.getFirstName(),
                    r.getLastName(),
                    r.isActive()));
  }
}
