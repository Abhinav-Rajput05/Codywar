package com.gourav.CodyWar.Exception;

/**
 * Thrown when async submission processing (Docker execution) fails.
 * Mapped to HTTP 500 INTERNAL SERVER ERROR by GlobalExceptionHandler.
 */
public class SubmissionProcessingException extends RuntimeException {

    public SubmissionProcessingException(String message) {
        super(message);
    }

    public SubmissionProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
