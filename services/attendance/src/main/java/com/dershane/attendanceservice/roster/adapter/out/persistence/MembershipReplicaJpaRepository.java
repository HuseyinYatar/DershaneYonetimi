package com.dershane.attendanceservice.roster.adapter.out.persistence;

import com.dershane.attendanceservice.roster.application.port.in.RosterEntryView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MembershipReplicaJpaRepository extends JpaRepository<MembershipReplicaJpaEntity, UUID> {

  Optional<MembershipReplicaJpaEntity> findFirstByStudentIdOrderByStartDateDesc(UUID studentId);

  @Query(
      """
      select new com.dershane.attendanceservice.roster.application.port.in.RosterEntryView(
          m.studentId, m.studentNumber, m.fullName)
      from MembershipReplicaJpaEntity m
      where m.classroomId = :classroomId and m.branchId = :branchId
        and m.startDate <= :date
        and (m.endDate is null or m.endDate >= :date)
      order by m.fullName, m.studentId
      """)
  List<RosterEntryView> findRosterOn(UUID classroomId, UUID branchId, LocalDate date);

  @Query(
      """
      select count(m) > 0 from MembershipReplicaJpaEntity m
      where m.classroomId = :classroomId and m.branchId = :branchId
        and m.studentId = :studentId
        and m.startDate <= :date
        and (m.endDate is null or m.endDate >= :date)
      """)
  boolean existsMembershipOn(UUID classroomId, UUID branchId, UUID studentId, LocalDate date);

  boolean existsByClassroomIdAndBranchIdNot(UUID classroomId, UUID branchId);
}
