package com.dershane.userservice.user.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface StudentJpaRepository
    extends JpaRepository<StudentJpaEntity, UUID>, JpaSpecificationExecutor<StudentJpaEntity> {

  /** Serializes classroom and guardian changes of one student. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from StudentJpaEntity s where s.id = :id")
  Optional<StudentJpaEntity> findByIdForUpdate(UUID id);

  @Query(
      "select count(s) > 0 from StudentJpaEntity s"
          + " where s.branchId = :branchId and s.studentNumber = :studentNumber")
  boolean existsStudentNumber(UUID branchId, String studentNumber);

  @Query(
      "select count(s) > 0 from StudentJpaEntity s"
          + " where s.branchId = :branchId and s.studentNumber = :studentNumber and s.id <> :id")
  boolean existsStudentNumberForOther(UUID branchId, String studentNumber, UUID id);
}
