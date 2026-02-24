package com.gourav.CodyWar.Domain.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal DTO representing the raw result of running user code inside a Docker container.
 * This is NOT exposed via the REST API — it is the contract between
 * {@code CodeExecutionService} (which runs Docker) and {@code SubmissionService}
 * (which judges the results against expected test case output).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {

    /**
     * Whether compilation and execution completed without a system-level failure.
     * A program that produces wrong output still has {@code success = true}.
     */
    private boolean success;

    /**
     * Standard output captured from the program (trimmed).
     */
    private String output;

    /**
     * Standard error captured from the program (compilation errors, stack traces, etc.).
     */
    private String errorOutput;

    /**
     * Process exit code returned by the container.
     * 0 = clean exit, 124 = timeout, non-zero = runtime error.
     */
    private int exitCode;

    /**
     * Wall-clock execution time measured by the host in milliseconds.
     */
    private long executionTimeMs;

    /**
     * Memory consumed by the container process in kilobytes.
     */
    private long memoryUsedKb;

    /**
     * True if the container was killed because it exceeded the time limit.
     */
    private boolean timedOut;

    /**
     * True if the container was killed because it exceeded the memory limit.
     */
    private boolean memoryExceeded;
}
