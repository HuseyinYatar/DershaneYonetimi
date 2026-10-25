package com.dershane.userservice.user.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "teacher")
@PrimaryKeyJoinColumn(name = "user_id")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class TeacherJpaEntity extends UserJpaEntity {

  @Column(name = "specialty_subject_id", nullable = false)
  private UUID specialtySubjectId;
}
