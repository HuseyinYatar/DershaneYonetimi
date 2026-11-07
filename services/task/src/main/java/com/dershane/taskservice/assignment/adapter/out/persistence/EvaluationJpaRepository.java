package com.dershane.taskservice.assignment.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EvaluationJpaRepository extends JpaRepository<EvaluationJpaEntity, UUID> {

  Optional<EvaluationJpaEntity> findBySubmissionId(UUID submissionId);
}
