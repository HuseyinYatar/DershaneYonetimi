package com.dershane.messagingservice.conversation.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MessageJpaRepository extends JpaRepository<MessageJpaEntity, UUID> {}
