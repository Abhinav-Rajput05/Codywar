package com.gourav.CodyWar.Domain.Dto;

import com.gourav.CodyWar.Domain.Entity.Language;
import com.gourav.CodyWar.Domain.Entity.SubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO returned to the frontend after a code submission.
 * Also broadcast via WebSocket to all battle participants.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponseDto {

    /** Unique identifier of this submission. */
    private UUID id;

    /** The battle this submission belongs to. */
    private UUID battleId;

    /** The user who submitted the code. */
    private UUID userId;

    /** Username of the submitter (for display purposes). */
    private String username;

    /** Programming language used. */
    private Language language;

    /** Current judging status of the submission. */
    private SubmissionStatus status;

    /** Number of test cases that passed. */
    private int testCasesPassed;

    /** Total number of test cases evaluated. */
    private int totalTestCases;

    /** Execution time in milliseconds (null if not yet judged). */
    private Long executionTimeMs;

    /** Memory used in kilobytes (null if not yet judged). */
    private Long memoryUsedKb;

    /** Compilation or runtime error message (null if no error). */
    private String errorMessage;

    /** Timestamp when the code was submitted. */
    private Instant submittedAt;

    /** Timestamp when judging completed (null if still pending/running). */
    private Instant judgedAt;
}
