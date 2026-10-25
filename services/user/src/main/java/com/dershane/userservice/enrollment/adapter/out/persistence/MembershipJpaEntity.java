package com.dershane.userservice.enrollment.adapter.out.persistence;

import com.dershane.userservice.shared.adapter.out.persistence.AuditableJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.UuidGenerator;

/** A student's stay in a classroom; at most one row per student is open ({@code end_date} null). */
@Entity
@Table(name = "classroom_membership")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString
class MembershipJpaEntity extends AuditableJpaEntity {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "student_id", nullable = false, updatable = false)
  private UUID studentId;

  @Column(name = "classroom_id", nullable = false, updatable = false)
  private UUID classroomId;

  @Column(name = "start_date", nullable = false, updatable = false)
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;
}
