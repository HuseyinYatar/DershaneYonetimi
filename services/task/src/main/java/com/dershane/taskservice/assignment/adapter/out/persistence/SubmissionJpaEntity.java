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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "submission")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
class SubmissionJpaEntity extends AuditableJpaEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @ToString.Include
  private UUID id;

  @Column(name = "assignment_id", nullable = false, updatable = false)
  private UUID assignmentId;

  @Column(name = "description")
  private String description;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "file_ids", nullable = false, columnDefinition = "uuid[]")
  private UUID[] fileIds;

  @Column(name = "submitted_at", nullable = false)
  private Instant submittedAt;

  @Column(name = "late", nullable = false)
  private boolean late;
}
