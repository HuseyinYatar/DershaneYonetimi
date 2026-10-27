package com.dershane.userservice.user.application.port.out;

import com.dershane.userservice.user.domain.User;
import java.util.Optional;
import java.util.UUID;

/** Polymorphic access: {@code findById} returns a Student, Teacher or Guardian when it is one. */
public interface LoadUserPort {

  Optional<User> findById(UUID userId);

  /** E-mail is stored lower-case (see {@code PersonalInfo}). */
  boolean existsByEmail(String email);

  boolean existsByEmailAndIdNot(String email, UUID userId);

  boolean existsByUsername(String username);
}
