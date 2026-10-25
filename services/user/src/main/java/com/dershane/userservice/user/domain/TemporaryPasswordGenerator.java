package com.dershane.userservice.user.domain;

import java.security.SecureRandom;

/**
 * Generates one-time passwords for accounts without e-mail. Ambiguous characters (0/O, 1/l/I) are
 * left out because the password is read out or written down for the student.
 */
public class TemporaryPasswordGenerator {

  private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
  private static final String DIGITS = "23456789";
  private static final String ALL = UPPER + LOWER + DIGITS;
  private static final int LENGTH = 12;

  private final SecureRandom random = new SecureRandom();

  public String generate() {
    char[] password = new char[LENGTH];
    password[0] = pick(UPPER);
    password[1] = pick(LOWER);
    password[2] = pick(DIGITS);
    for (int i = 3; i < LENGTH; i++) {
      password[i] = pick(ALL);
    }
    shuffle(password);
    return new String(password);
  }

  private char pick(String alphabet) {
    return alphabet.charAt(random.nextInt(alphabet.length()));
  }

  private void shuffle(char[] chars) {
    for (int i = chars.length - 1; i > 0; i--) {
      int j = random.nextInt(i + 1);
      char tmp = chars[i];
      chars[i] = chars[j];
      chars[j] = tmp;
    }
  }
}
