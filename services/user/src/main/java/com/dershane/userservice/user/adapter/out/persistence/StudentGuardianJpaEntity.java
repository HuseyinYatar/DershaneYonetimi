package com.dershane.userservice.user.adapter.out.persistence;

import com.dershane.userservice.shared.adapter.out.persistence.AuditableJpaEntity;
import com.dershane.userservice.user.domain.Relationship;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Diagram: VeliOgrenci. A student has at most one guardian (unique {@code student_id}). */
@Entity
@Table(name = "student_guardian")
@IdClass(StudentGuardianJpaEntity.Key.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
class StudentGuardianJpaEntity extends AuditableJpaEntity {

  @Id
  @Column(name = "guardian_id", nullable = false, updatable = false)
  private UUID guardianId;

  @Id
  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "relationship", nullable = false, length = 16)
  private Relationship relationship;

  /** Composite primary key (guardian_id, student_id). */
  @Getter
  @NoArgsConstructor
  @EqualsAndHashCode
  public static class Key implements Serializable {
    private UUID guardianId;
    private UUID studentId;

    public Key(UUID guardianId, UUID studentId) {
      this.guardianId = guardianId;
      this.studentId = studentId;
    }
  }
}
