package com.gourav.CodyWar.Domain.Dto;

import com.gourav.CodyWar.Domain.Entity.BattleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BattleResponseDto {

    private UUID id;
    private String roomCode;
    private BattleStatus status;
    private int maxParticipants;
    private int currentParticipants;
    private int durationSeconds;
    private boolean isPrivate;
    private ProblemResponseDto problem;
    private List<ParticipantDto> participants;
    private UserDto winner;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private Long remainingTimeSeconds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParticipantDto {
        private UUID id;
        private UUID oduserId;
        private String username;
        private boolean isReady;
        private boolean hasSubmitted;
        private int score;
        private Instant joinedAt;
    }
}
