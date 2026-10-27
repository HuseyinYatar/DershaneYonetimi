package com.dershane.userservice.shared.adapter.in.web.idempotency;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a creating endpoint that honours the optional {@code Idempotency-Key} header: a retried
 * request with the same key and body returns the first response instead of creating a duplicate
 * (e.g. a second Keycloak account). The method must return a {@code ResponseEntity}.
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {}
