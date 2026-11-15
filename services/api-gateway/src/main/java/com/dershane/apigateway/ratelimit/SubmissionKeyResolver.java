package com.dershane.apigateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Same subject as {@link SubjectKeyResolver} with a prefix. The Redis limiter keys its buckets by
 * this string only (not by route), so without the prefix the submission limit and the default limit
 * would share one bucket with different capacities.
 */
@Component
public class SubmissionKeyResolver implements KeyResolver {

  private final SubjectKeyResolver subject;

  public SubmissionKeyResolver(SubjectKeyResolver subject) {
    this.subject = subject;
  }

  @Override
  public Mono<String> resolve(ServerWebExchange exchange) {
    return subject.resolve(exchange).map(sub -> "submission:" + sub);
  }
}
