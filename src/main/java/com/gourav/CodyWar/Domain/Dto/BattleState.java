package com.gourav.CodyWar.Domain.Dto;

import com.gourav.CodyWar.Domain.Entity.BattleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-stored battle state for real-time synchronization.
 * This is stored in Redis for fast access and synchronization across instances.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleState implements Serializable {

    private UUID battleId;
    private String roomCode;
    private UUID problemId;
    private BattleStatus status;
    private int maxParticipants;
    private int durationSeconds;
    private boolean isPrivate;
    
    @Builder.Default
    private Set<ParticipantState> participants = new HashSet<>();
    
    private UUID winnerId;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantState implements Serializable {
        private UUID oduserId;
        private String username;
        private boolean isReady;
        private boolean hasSubmitted;
        private int score;
        private Instant joinedAt;
        private UUID lastSubmissionId;
    }

    public boolean isFull() {
        return participants.size() >= maxParticipants;
    }

    public boolean allParticipantsReady() {
        return participants.size() >= 2 && 
               participants.stream().allMatch(ParticipantState::isReady);
    }

    public long getRemainingTimeSeconds() {
        if (startedAt == null || status != BattleStatus.IN_PROGRESS) {
            return durationSeconds;
        }
        long elapsed = Instant.now().getEpochSecond() - startedAt.getEpochSecond();
        return Math.max(0, durationSeconds - elapsed);
    }
}
