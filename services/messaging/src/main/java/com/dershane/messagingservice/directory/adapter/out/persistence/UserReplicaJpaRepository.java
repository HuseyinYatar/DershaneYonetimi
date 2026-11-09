package com.dershane.messagingservice.directory.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserReplicaJpaRepository extends JpaRepository<UserReplicaJpaEntity, UUID> {}
