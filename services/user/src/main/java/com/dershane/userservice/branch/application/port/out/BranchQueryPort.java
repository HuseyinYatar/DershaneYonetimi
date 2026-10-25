package com.dershane.userservice.branch.application.port.out;

import com.dershane.userservice.branch.application.port.in.BranchView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BranchQueryPort {

  Optional<BranchView> findViewById(UUID branchId);

  Page<BranchView> findViews(Pageable pageable);

  /** A page containing only the given branch (what a branch admin sees). */
  Page<BranchView> findViewsOf(UUID branchId, Pageable pageable);
}
