package com.dershane.userservice.user.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Name and contact columns of {@code users}. Personal data: never printed. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PersonalInfoJpa {

  @Column(name = "first_name", nullable = false, length = 80)
  private String firstName;

  @Column(name = "last_name", nullable = false, length = 80)
  private String lastName;

  @Column(name = "email", length = 254)
  private String email;

  @Column(name = "phone", length = 20)
  private String phone;

  @Override
  public String toString() {
    return "PersonalInfoJpa[masked]";
  }
}
