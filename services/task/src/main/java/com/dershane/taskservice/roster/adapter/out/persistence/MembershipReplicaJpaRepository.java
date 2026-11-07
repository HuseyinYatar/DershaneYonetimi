package com.dershane.taskservice.roster.adapter.out.persistence;

import com.dershane.taskservice.roster.application.port.in.ActiveMembershipView;
import com.dershane.taskservice.roster.application.port.in.RosterEntryView;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MembershipReplicaJpaRepository extends JpaRepository<MembershipReplicaJpaEntity, UUID> {

  Optional<MembershipReplicaJpaEntity> findFirstByStudentIdOrderByStartDateDesc(UUID studentId);

  @Query(
      """
      select new com.dershane.taskservice.roster.application.port.in.RosterEntryView(
          m.studentId, m.studentNumber, m.fullName)
      from MembershipReplicaJpaEntity m
      where m.classroomId = :classroomId
        and m.startDate <= :date
        and (m.endDate is null or m.endDate >= :date)
      order by m.fullName, m.studentId
      """)
  List<RosterEntryView> findRosterOn(UUID classroomId, LocalDate date);

  @Query(
      """
      select count(m) > 0 from MembershipReplicaJpaEntity m
      where m.classroomId = :classroomId and m.studentId = :studentId
        and m.startDate <= :date
        and (m.endDate is null or m.endDate >= :date)
      """)
  boolean existsMembershipOn(UUID classroomId, UUID studentId, LocalDate date);

  @Query(
      """
      select m.branchId from MembershipReplicaJpaEntity m
      where m.classroomId = :classroomId
        and m.startDate <= :date
        and (m.endDate is null or m.endDate >= :date)
      order by m.id
      """)
  List<UUID> findBranchIdsOn(UUID classroomId, LocalDate date, Pageable pageable);

  @Query(
      """
      select new com.dershane.taskservice.roster.application.port.in.ActiveMembershipView(
          m.classroomId, m.branchId)
      from MembershipReplicaJpaEntity m
      where m.studentId = :studentId
        and m.startDate <= :date
        and (m.endDate is null or m.endDate >= :date)
      order by m.startDate desc, m.id
      """)
  List<ActiveMembershipView> findActiveMemberships(
      UUID studentId, LocalDate date, Pageable pageable);
}
