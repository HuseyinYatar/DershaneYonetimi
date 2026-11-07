package com.dershane.taskservice.assignment.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SubmissionJpaRepository extends JpaRepository<SubmissionJpaEntity, UUID> {

  Optional<SubmissionJpaEntity> findByAssignmentId(UUID assignmentId);
}
