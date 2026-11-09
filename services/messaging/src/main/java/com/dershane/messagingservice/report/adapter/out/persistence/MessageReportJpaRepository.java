package com.dershane.messagingservice.report.adapter.out.persistence;

import com.dershane.messagingservice.report.domain.ReportStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MessageReportJpaRepository extends JpaRepository<MessageReportJpaEntity, UUID> {

  boolean existsByMessageIdAndReporterId(UUID messageId, UUID reporterId);

  @Query(
      "select r from MessageReportJpaEntity r"
          + " where (:branchId is null or r.branchId = :branchId)"
          + " and (:status is null or r.status = :status)"
          + " order by r.createdAt desc")
  Page<MessageReportJpaEntity> search(
      @Param("branchId") UUID branchId, @Param("status") ReportStatus status, Pageable pageable);
}
