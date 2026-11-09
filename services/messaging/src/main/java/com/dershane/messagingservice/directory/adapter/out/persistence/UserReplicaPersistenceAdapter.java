package com.dershane.messagingservice.directory.adapter.out.persistence;

import com.dershane.messagingservice.directory.application.port.out.UserReplicaPort;
import com.dershane.messagingservice.directory.domain.UserReplica;
import com.dershane.messagingservice.shared.adapter.out.persistence.VersionChecks;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class UserReplicaPersistenceAdapter implements UserReplicaPort {

  private final UserReplicaJpaRepository replicas;

  @Override
  public Optional<UserReplica> findById(UUID userId) {
    return replicas.findById(userId).map(UserReplicaPersistenceAdapter::toDomain);
  }

  @Override
  public void save(UserReplica replica) {
    UserReplicaJpaEntity entity;
    if (replica.getVersion() == null) {
      entity = new UserReplicaJpaEntity();
      entity.setUserId(replica.getUserId());
    } else {
      entity = replicas.findById(replica.getUserId()).orElseThrow();
      VersionChecks.requireCurrent(
          log, "UserReplica", replica.getUserId(), replica.getVersion(), entity.getVersion());
    }
    entity.setRole(replica.getRole());
    entity.setBranchId(replica.getBranchId());
    entity.setFirstName(replica.getFirstName());
    entity.setLastName(replica.getLastName());
    entity.setActive(replica.isActive());
    entity.setRecordVersion(replica.getRecordVersion());
    replicas.save(entity);
  }

  private static UserReplica toDomain(UserReplicaJpaEntity e) {
    return new UserReplica(
        e.getUserId(),
        e.getVersion(),
        e.getRole(),
        e.getBranchId(),
        e.getFirstName(),
        e.getLastName(),
        e.isActive(),
        e.getRecordVersion());
  }
}
