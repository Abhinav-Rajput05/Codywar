package com.gourav.CodyWar.Domain.Dto;

import com.gourav.CodyWar.Domain.Entity.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO sent by the frontend when a user submits code in a battle.
 * Contains all the data needed to create a Submission entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionRequestDto {

    /** The battle this submission belongs to. */
    @NotNull(message = "Battle ID is required")
    private UUID battleId;

    /** The problem being solved. */
    @NotNull(message = "Problem ID is required")
    private UUID problemId;

    /** Programming language used for the submission. */
    @NotNull(message = "Language is required")
    private Language language;

    /** The user's source code. */
    @NotBlank(message = "Code cannot be empty")
    @Size(max = 50000, message = "Code exceeds maximum length of 50,000 characters")
    private String code;
}