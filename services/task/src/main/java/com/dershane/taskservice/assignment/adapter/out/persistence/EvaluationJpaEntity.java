package com.dershane.taskservice.assignment.adapter.out.persistence;

import com.dershane.taskservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "evaluation")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class EvaluationJpaEntity extends AuditableJpaEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @ToString.Include
  private UUID id;

  @Column(name = "submission_id", nullable = false, updatable = false)
  private UUID submissionId;

  @Column(name = "evaluator_id", nullable = false)
  private UUID evaluatorId;

  @Column(name = "score")
  private Integer score;

  @Column(name = "feedback")
  private String feedback;

  @Column(name = "evaluated_at", nullable = false)
  private Instant evaluatedAt;
}
