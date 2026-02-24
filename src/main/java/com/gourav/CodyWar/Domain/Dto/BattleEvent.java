package com.gourav.CodyWar.Domain.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * WebSocket event sent to clients for real-time battle updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleEvent implements Serializable {

    private EventType type;
    private UUID battleId;
    private String roomCode;
    private Object payload;
    
    @Builder.Default
    private Instant timestamp = Instant.now();

    public enum EventType {
        // Room events
        PLAYER_JOINED,
        PLAYER_LEFT,
        PLAYER_READY,
        
        // Battle lifecycle events
        BATTLE_STARTING,   // Countdown before start
        TIMER_START,       // Battle has started
        TIMER_UPDATE,      // Periodic timer update
        BATTLE_ENDED,      // Battle time is up
        
        // Submission events
        SUBMISSION_RECEIVED,
        SUBMISSION_JUDGED,
        SUBMISSION_UPDATE,
        
        // Result events
        WINNER_ANNOUNCEMENT,
        BATTLE_CANCELLED,
        
        // System events
        ERROR,
        HEARTBEAT
    }
}
