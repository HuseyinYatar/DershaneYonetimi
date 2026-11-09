package com.dershane.messagingservice.realtime.adapter.out.messaging;

import com.dershane.messagingservice.realtime.application.port.out.TicketStorePort;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RedisTicketStore implements TicketStorePort {

  private static final String PREFIX = "chat-ticket:";

  private final StringRedisTemplate redis;

  @Override
  public void store(String ticket, UUID userId, Duration ttl) {
    redis.opsForValue().set(PREFIX + ticket, userId.toString(), ttl);
  }

  @Override
  public Optional<UUID> consume(String ticket) {
    return Optional.ofNullable(redis.opsForValue().getAndDelete(PREFIX + ticket))
        .map(UUID::fromString);
  }
}
