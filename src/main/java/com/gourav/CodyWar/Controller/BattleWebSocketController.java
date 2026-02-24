package com.gourav.CodyWar.Controller;

import com.gourav.CodyWar.Domain.Dto.BattleEvent;
import com.gourav.CodyWar.Domain.Dto.BattleState;
import com.gourav.CodyWar.Security.CustomUserDetails;
import com.gourav.CodyWar.Service.BattleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class BattleWebSocketController {

    private final BattleService battleService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle player joining a battle room via WebSocket.
     * Client sends: /app/battle/{battleId}/join
     * Broadcasts to: /topic/battle/{battleId}
     */
    @MessageMapping("/battle/{battleId}/join")
    @SendTo("/topic/battle/{battleId}")
    public BattleEvent handlePlayerJoin(
            @DestinationVariable UUID battleId,
            SimpMessageHeaderAccessor headerAccessor) {
        
        CustomUserDetails userDetails = extractUserDetails(headerAccessor);
        if (userDetails == null) {
            return createErrorEvent(battleId, "Unauthorized");
        }

        log.info("WebSocket: User {} joined battle room {}", userDetails.getUser().getId(), battleId);

        BattleState state = battleService.getBattleState(battleId);
        
        return BattleEvent.builder()
                .type(BattleEvent.EventType.PLAYER_JOINED)
                .battleId(battleId)
                .roomCode(state != null ? state.getRoomCode() : null)
                .payload(Map.of(
                        "userId", userDetails.getUser().getId(),
                        "username", userDetails.getUsername(),
                        "battleState", state != null ? state : Map.of()
                ))
                .build();
    }

    /**
     * Handle player ready status change via WebSocket.
     * Client sends: /app/battle/{battleId}/ready
     * Broadcasts to: /topic/battle/{battleId}
     */
    @MessageMapping("/battle/{battleId}/ready")
    public void handlePlayerReady(
            @DestinationVariable UUID battleId,
            Map<String, Object> payload,
            SimpMessageHeaderAccessor headerAccessor) {
        
        CustomUserDetails userDetails = extractUserDetails(headerAccessor);
        if (userDetails == null) {
            sendErrorToUser(headerAccessor, "Unauthorized");
            return;
        }

        boolean ready = payload.containsKey("ready") && Boolean.TRUE.equals(payload.get("ready"));

        try {
            battleService.setPlayerReady(userDetails.getUser().getId(), battleId, ready);
            // BattleService will broadcast the event
        } catch (Exception e) {
            log.error("Error setting player ready: {}", e.getMessage());
            sendErrorToUser(headerAccessor, e.getMessage());
        }
    }

    /**
     * Handle player leaving battle via WebSocket.
     * Client sends: /app/battle/{battleId}/leave
     * Broadcasts to: /topic/battle/{battleId}
     */
    @MessageMapping("/battle/{battleId}/leave")
    public void handlePlayerLeave(
            @DestinationVariable UUID battleId,
            SimpMessageHeaderAccessor headerAccessor) {
        
        CustomUserDetails userDetails = extractUserDetails(headerAccessor);
        if (userDetails == null) {
            return;
        }

        try {
            battleService.leaveBattle(userDetails.getUser().getId(), battleId);
            // BattleService will broadcast the event
        } catch (Exception e) {
            log.error("Error leaving battle: {}", e.getMessage());
            sendErrorToUser(headerAccessor, e.getMessage());
        }
    }

    /**
     * Handle heartbeat/ping from client.
     * Client sends: /app/battle/{battleId}/heartbeat
     * Response to: /user/queue/heartbeat
     */
    @MessageMapping("/battle/{battleId}/heartbeat")
    @SendToUser("/queue/heartbeat")
    public BattleEvent handleHeartbeat(@DestinationVariable UUID battleId) {
        BattleState state = battleService.getBattleState(battleId);
        
        return BattleEvent.builder()
                .type(BattleEvent.EventType.HEARTBEAT)
                .battleId(battleId)
                .payload(Map.of(
                        "remainingSeconds", state != null ? state.getRemainingTimeSeconds() : 0,
                        "status", state != null ? state.getStatus() : "UNKNOWN"
                ))
                .build();
    }

    /**
     * Request current battle state.
     * Client sends: /app/battle/{battleId}/state
     * Response to: /user/queue/battle-state
     */
    @MessageMapping("/battle/{battleId}/state")
    @SendToUser("/queue/battle-state")
    public BattleEvent handleStateRequest(@DestinationVariable UUID battleId) {
        BattleState state = battleService.getBattleState(battleId);
        
        if (state == null) {
            return createErrorEvent(battleId, "Battle not found");
        }

        return BattleEvent.builder()
                .type(BattleEvent.EventType.TIMER_UPDATE)
                .battleId(battleId)
                .roomCode(state.getRoomCode())
                .payload(state)
                .build();
    }

    // ==================== HELPER METHODS ====================

    private CustomUserDetails extractUserDetails(SimpMessageHeaderAccessor headerAccessor) {
        if (headerAccessor.getUser() instanceof UsernamePasswordAuthenticationToken auth) {
            if (auth.getPrincipal() instanceof CustomUserDetails) {
                return (CustomUserDetails) auth.getPrincipal();
            }
        }
        return null;
    }

    private BattleEvent createErrorEvent(UUID battleId, String message) {
        return BattleEvent.builder()
                .type(BattleEvent.EventType.ERROR)
                .battleId(battleId)
                .payload(Map.of("error", message))
                .build();
    }

    private void sendErrorToUser(SimpMessageHeaderAccessor headerAccessor, String message) {
        String sessionId = headerAccessor.getSessionId();
        if (sessionId != null) {
            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/errors",
                    BattleEvent.builder()
                            .type(BattleEvent.EventType.ERROR)
                            .payload(Map.of("error", message))
                            .timestamp(Instant.now())
                            .build(),
                    Map.of()
            );
        }
    }
}
