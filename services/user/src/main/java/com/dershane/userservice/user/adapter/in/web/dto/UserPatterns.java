package com.dershane.userservice.user.adapter.in.web.dto;

/** Input formats shared by the request records. */
final class UserPatterns {

  /** At least ten digits; the separators alone are not a number. */
  static final String PHONE = "^(?=(?:\\D*\\d){10})\\+?[0-9 ()-]{10,20}$";

  /** Contains a visible character; a name of blanks only would blank out the stored name. */
  static final String NOT_BLANK = ".*\\S.*";

  static final String STUDENT_NUMBER = "^[0-9A-Za-z-]{1,20}$";

  private UserPatterns() {}
}
