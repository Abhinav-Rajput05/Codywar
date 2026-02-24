package com.gourav.CodyWar.Domain.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Player in matchmaking queue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchmakingEntry implements Serializable {

    private UUID oduserId;
    private String username;
    private int ratingScore;
    private Instant queuedAt;

    @Builder.Default
    private int preferredDuration = 1800;  // 30 minutes

    @Builder.Default
    private int maxParticipants = 2;  // 1v1 by default
}
