package com.gourav.CodyWar.Domain.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result returned by the Docker engine after executing a submission.
 * Used by SubmissionService to update the Submission entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DockerExecutionResult {

    private boolean success;

    private int passedTestCases;

    private int totalTestCases;

    private Integer executionTimeMs;

    private Integer memoryUsedKb;

    private String errorMessage;

    // Helper methods for status determination
    public boolean isAllPassed() {
        return success && passedTestCases == totalTestCases;
    }

    public boolean hasCompilationError() {
        return errorMessage != null && errorMessage.contains("Compilation Error");
    }

    public boolean isTimeLimitExceeded() {
        return errorMessage != null && errorMessage.contains("Time Limit Exceeded");
    }

    public boolean isMemoryLimitExceeded() {
        return errorMessage != null && errorMessage.contains("Memory Limit Exceeded");
    }
}
