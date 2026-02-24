package com.gourav.CodyWar.Domain.Dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBattleRequest {

    private UUID problemId;  // Optional: if not provided, random problem will be assigned

    @Builder.Default
    private boolean isPrivate = false;

    @Min(2)
    @Max(10)
    @Builder.Default
    private int maxParticipants = 2;

    @Min(300)  // Minimum 5 minutes
    @Max(7200) // Maximum 2 hours
    @Builder.Default
    private int durationSeconds = 1800;  // 30 minutes default
}
