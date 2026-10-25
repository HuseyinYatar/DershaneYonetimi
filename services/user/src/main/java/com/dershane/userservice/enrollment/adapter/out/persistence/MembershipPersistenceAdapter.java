package com.dershane.userservice.enrollment.adapter.out.persistence;

import com.dershane.userservice.enrollment.application.port.out.LoadMembershipPort;
import com.dershane.userservice.enrollment.application.port.out.SaveMembershipPort;
import com.dershane.userservice.enrollment.domain.ClassroomMembership;
import com.dershane.userservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class MembershipPersistenceAdapter implements LoadMembershipPort, SaveMembershipPort {

  private final MembershipJpaRepository repository;
  private final EnrollmentPersistenceMapper mapper;

  @Override
  public Optional<ClassroomMembership> findActiveByStudentId(UUID studentId) {
    return repository.findByStudentIdAndEndDateIsNull(studentId).map(mapper::toDomain);
  }

  @Override
  public ClassroomMembership save(ClassroomMembership membership) {
    return mapper.toDomain(persist(membership));
  }

  @Override
  public ClassroomMembership saveAndFlush(ClassroomMembership membership) {
    MembershipJpaEntity entity = persist(membership);
    repository.flush();
    return mapper.toDomain(entity);
  }

  private MembershipJpaEntity persist(ClassroomMembership membership) {
    if (membership.getVersion() == null) {
      return repository.save(mapper.toEntity(membership));
    }
    MembershipJpaEntity entity =
        repository
            .findById(membership.getId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.ACTIVE_MEMBERSHIP_NOT_FOUND,
                        "Membership to save no longer exists",
                        "membershipId",
                        membership.getId()));
    VersionChecks.requireCurrent(
        log,
        "ClassroomMembership",
        membership.getId(),
        membership.getVersion(),
        entity.getVersion());
    mapper.update(entity, membership);
    return entity;
  }
}
