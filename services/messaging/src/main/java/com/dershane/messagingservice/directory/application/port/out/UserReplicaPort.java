package com.dershane.messagingservice.directory.application.port.out;

import com.dershane.messagingservice.directory.domain.UserReplica;
import java.util.Optional;
import java.util.UUID;

public interface UserReplicaPort {

  Optional<UserReplica> findById(UUID userId);

  void save(UserReplica replica);
}
