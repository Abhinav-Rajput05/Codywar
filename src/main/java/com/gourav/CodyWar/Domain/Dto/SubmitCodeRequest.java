package com.gourav.CodyWar.Domain.Dto;

import com.gourav.CodyWar.Domain.Entity.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitCodeRequest {

    /**
     * The battle this submission belongs to.
     */
    @NotNull(message = "Battle ID is required")
    private UUID battleId;

    /**
     * The programming language used for this submission.
     */
    @NotNull(message = "Language is required")
    private Language language;

    /**
     * The source code submitted by the user.
     */
    @NotBlank(message = "Code is required")
    private String code;
}
