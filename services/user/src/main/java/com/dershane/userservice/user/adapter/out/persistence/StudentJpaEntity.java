package com.dershane.userservice.user.adapter.out.persistence;

import com.dershane.userservice.user.domain.TargetExam;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "student")
@PrimaryKeyJoinColumn(name = "user_id")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class StudentJpaEntity extends UserJpaEntity {

  /**
   * Copy of {@code users.branch_id} so the database can enforce "student number is unique within a
   * branch". It follows {@link #setBranchId}, written once on insert; the composite foreign key
   * with ON UPDATE CASCADE keeps it in sync afterwards.
   */
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID studentBranchId;

  @Column(name = "student_number", nullable = false, length = 20)
  private String studentNumber;

  @Column(name = "grade_level", nullable = false)
  private int gradeLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_exam", nullable = false, length = 8)
  private TargetExam targetExam;

  @Override
  public void setBranchId(UUID branchId) {
    super.setBranchId(branchId);
    this.studentBranchId = branchId;
  }
}
