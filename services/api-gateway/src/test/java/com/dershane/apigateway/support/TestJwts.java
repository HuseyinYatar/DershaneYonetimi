package com.dershane.apigateway.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;

/**
 * Signs tokens with a throwaway RSA key and serves its public half as a JWKS, so tests exercise the
 * real decoder chain (signature, issuer, audience) without a Keycloak container.
 */
public final class TestJwts {

  public static final String ISSUER = "https://issuer.test/realms/dershane";
  public static final String AUDIENCE = "dershane-api";

  private static final String KEY_ID = "test-key";

  private final RSAKey key;

  public TestJwts() {
    try {
      this.key = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("Could not generate the test signing key", e);
    }
  }

  public String jwks() {
    return new JWKSet(key.toPublicJWK()).toString();
  }

  public String token(String subject) {
    return token(subject, ISSUER, AUDIENCE, Instant.now().plusSeconds(300));
  }

  public String token(String subject, String issuer, String audience, Instant expiresAt) {
    JWTClaimsSet.Builder claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject(subject)
            .issueTime(new Date())
            .expirationTime(Date.from(expiresAt));
    if (audience != null) {
      claims.audience(audience);
    }
    try {
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), claims.build());
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Could not sign the test token", e);
    }
  }
}
