package com.gourav.CodyWar.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gourav.CodyWar.Domain.Dto.BattleEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.UUID;

/**
 * Redis Pub/Sub service for broadcasting battle events across multiple application instances.
 * This enables horizontal scaling of WebSocket connections.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BattleEventPublisher implements MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    private static final String BATTLE_EVENTS_CHANNEL = "battle-events";

    @PostConstruct
    public void init() {
        // Subscribe to battle events channel
        listenerContainer.addMessageListener(this, new ChannelTopic(BATTLE_EVENTS_CHANNEL));
        log.info("Subscribed to Redis channel: {}", BATTLE_EVENTS_CHANNEL);
    }

    /**
     * Publish a battle event to all application instances via Redis Pub/Sub.
     * This ensures that all WebSocket clients connected to any instance receive the event.
     */
    public void publishEvent(BattleEvent event) {
        try {
            redisTemplate.convertAndSend(BATTLE_EVENTS_CHANNEL, event);
            log.debug("Published event to Redis: {} for battle {}", event.getType(), event.getBattleId());
        } catch (Exception e) {
            log.error("Failed to publish event to Redis", e);
            // Fallback to local broadcast
            broadcastLocally(event);
        }
    }

    /**
     * Handle incoming messages from Redis Pub/Sub.
     * This method is called when a message is received on the subscribed channel.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            BattleEvent event = objectMapper.readValue(body, BattleEvent.class);
            broadcastLocally(event);
            log.debug("Received and broadcast event from Redis: {} for battle {}", event.getType(), event.getBattleId());
        } catch (Exception e) {
            log.error("Failed to process Redis message", e);
        }
    }

    /**
     * Broadcast event to WebSocket clients connected to this instance.
     */
    private void broadcastLocally(BattleEvent event) {
        if (event.getBattleId() != null) {
            String destination = "/topic/battle/" + event.getBattleId();
            messagingTemplate.convertAndSend(destination, event);
        }
    }

    /**
     * Publish event to a specific user.
     */
    public void publishToUser(UUID userId, BattleEvent event) {
        String destination = "/user/" + userId + "/queue/notifications";
        messagingTemplate.convertAndSend(destination, event);
    }
}
