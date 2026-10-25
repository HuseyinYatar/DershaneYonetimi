package com.dershane.userservice.user.adapter.in.web.dto;

/** Input formats shared by the request records. */
final class UserPatterns {

  static final String PHONE = "^\\+?[0-9 ()-]{10,20}$";
  static final String STUDENT_NUMBER = "^[0-9A-Za-z-]{1,20}$";

  private UserPatterns() {}
}
