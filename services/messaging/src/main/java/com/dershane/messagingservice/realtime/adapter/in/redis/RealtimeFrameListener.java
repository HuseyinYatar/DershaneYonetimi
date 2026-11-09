package com.dershane.messagingservice.realtime.adapter.in.redis;

import com.dershane.messagingservice.realtime.adapter.in.ws.SessionRegistry;
import com.dershane.messagingservice.shared.application.MessagingProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every pod listens to the frame channel and delivers to the sockets it holds. If Redis is down at
 * startup the container keeps retrying; the service still serves REST.
 */
@Slf4j
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
class RealtimeFrameListener implements MessageListener {

  private final SessionRegistry registry;
  private final JsonMapper json;

  @Bean
  RedisMessageListenerContainer frameListenerContainer(
      RedisConnectionFactory connectionFactory, MessagingProperties properties) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener(this, new ChannelTopic(properties.realtime().channel()));
    return container;
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      JsonNode envelope = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
      List<UUID> recipients = new ArrayList<>();
      envelope.path("recipients").forEach(r -> recipients.add(UUID.fromString(r.asString())));
      registry.send(recipients, envelope.path("frame").toString());
    } catch (RuntimeException e) {
      log.atWarn().setCause(e).log("realtime.frame_rejected");
    }
  }
}
