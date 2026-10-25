package com.dershane.userservice.enrollment.adapter.out.persistence;

import com.dershane.userservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "teacher_assignment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
class TeacherAssignmentJpaEntity extends AuditableJpaEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "teacher_id", nullable = false, updatable = false)
  private UUID teacherId;

  @Column(name = "classroom_id", nullable = false, updatable = false)
  private UUID classroomId;

  @Column(name = "subject_id", nullable = false, updatable = false)
  private UUID subjectId;
}
