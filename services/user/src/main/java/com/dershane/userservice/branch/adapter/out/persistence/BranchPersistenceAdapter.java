package com.dershane.userservice.branch.adapter.out.persistence;

import com.dershane.userservice.branch.application.port.in.BranchView;
import com.dershane.userservice.branch.application.port.out.BranchQueryPort;
import com.dershane.userservice.branch.application.port.out.LoadBranchPort;
import com.dershane.userservice.branch.application.port.out.SaveBranchPort;
import com.dershane.userservice.branch.domain.Branch;
import com.dershane.userservice.shared.adapter.out.persistence.VersionChecks;
import com.dershane.userservice.shared.domain.ErrorCode;
import com.dershane.userservice.shared.domain.Rejections;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class BranchPersistenceAdapter implements LoadBranchPort, SaveBranchPort, BranchQueryPort {

  private final BranchJpaRepository repository;
  private final BranchPersistenceMapper mapper;

  @Override
  public Optional<Branch> findById(UUID branchId) {
    return repository.findById(branchId).map(mapper::toDomain);
  }

  @Override
  public boolean existsByCode(String code) {
    return repository.existsByCode(code);
  }

  @Override
  public Branch save(Branch branch) {
    if (branch.getVersion() == null) {
      return mapper.toDomain(repository.save(mapper.toEntity(branch)));
    }
    BranchJpaEntity entity =
        repository
            .findById(branch.getId())
            .orElseThrow(
                () ->
                    Rejections.reject(
                        log,
                        ErrorCode.BRANCH_NOT_FOUND,
                        "Branch to save no longer exists",
                        "branchId",
                        branch.getId()));
    VersionChecks.requireCurrent(
        log, "Branch", branch.getId(), branch.getVersion(), entity.getVersion());
    mapper.update(entity, branch);
    return mapper.toDomain(entity);
  }

  @Override
  public Optional<BranchView> findViewById(UUID branchId) {
    return repository.findById(branchId).map(mapper::toView);
  }

  @Override
  public Page<BranchView> findViews(Pageable pageable) {
    return repository.findAll(pageable).map(mapper::toView);
  }

  @Override
  public Page<BranchView> findViewsOf(UUID branchId, Pageable pageable) {
    return repository.findPageById(branchId, pageable).map(mapper::toView);
  }
}
