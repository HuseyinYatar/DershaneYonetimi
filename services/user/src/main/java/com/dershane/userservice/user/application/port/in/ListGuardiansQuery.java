package com.dershane.userservice.user.application.port.in;

import com.dershane.userservice.shared.application.Actor;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListGuardiansQuery {

  Page<GuardianView> list(Actor actor, UUID branchId, Boolean active, Pageable pageable);
}
