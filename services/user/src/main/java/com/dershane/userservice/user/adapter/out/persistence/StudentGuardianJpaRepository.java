package com.dershane.userservice.user.adapter.out.persistence;

import com.dershane.userservice.user.application.port.in.GuardianChildView;
import com.dershane.userservice.user.application.port.in.StudentGuardianView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface StudentGuardianJpaRepository
    extends JpaRepository<StudentGuardianJpaEntity, StudentGuardianJpaEntity.Key> {

  Optional<StudentGuardianJpaEntity> findByStudentId(UUID studentId);

  boolean existsByGuardianIdAndStudentId(UUID guardianId, UUID studentId);

  @Query(
      """
      select new com.dershane.userservice.user.application.port.in.StudentGuardianView(
          g.id, sg.studentId, g.info.firstName, g.info.lastName, g.info.email, g.info.phone,
          sg.relationship)
      from StudentGuardianJpaEntity sg join GuardianJpaEntity g on g.id = sg.guardianId
      where sg.studentId = :studentId
      """)
  Optional<StudentGuardianView> findGuardianView(UUID studentId);

  @Query(
      """
      select new com.dershane.userservice.user.application.port.in.GuardianChildView(
          s.id, s.info.firstName, s.info.lastName, s.studentNumber, s.gradeLevel,
          sg.relationship, s.active)
      from StudentGuardianJpaEntity sg join StudentJpaEntity s on s.id = sg.studentId
      where sg.guardianId = :guardianId
      order by s.info.firstName, s.info.lastName
      """)
  List<GuardianChildView> findChildViews(UUID guardianId);
}
