package com.dershane.messagingservice.realtime.adapter.out.messaging;

import com.dershane.messagingservice.conversation.application.port.out.PresencePort;
import com.dershane.messagingservice.realtime.application.port.out.PresenceStore;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** One key per online user; it expires unless a heartbeat renews it. */
@Slf4j
@Component
@RequiredArgsConstructor
class RedisPresenceAdapter implements PresenceStore, PresencePort {

  private static final String PREFIX = "presence:";

  private final StringRedisTemplate redis;

  @Override
  public void markOnline(UUID userId, Duration ttl) {
    redis.opsForValue().set(PREFIX + userId, "1", ttl);
  }

  @Override
  public void clear(UUID userId) {
    redis.delete(PREFIX + userId);
  }

  @Override
  public boolean isOnline(UUID userId) {
    try {
      return Boolean.TRUE.equals(redis.hasKey(PREFIX + userId));
    } catch (RuntimeException e) {
      log.atWarn().addKeyValue("userId", userId).setCause(e).log("presence.lookup_failed");
      return false;
    }
  }
}
