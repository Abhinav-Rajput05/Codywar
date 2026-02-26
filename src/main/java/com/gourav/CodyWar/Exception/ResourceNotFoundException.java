package com.gourav.CodyWar.Exception;

/**
 * Thrown when a requested entity (User, Battle, Submission, Problem)
 * is not found in the database.
 * Mapped to HTTP 404 NOT FOUND by GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceName;
    private final Object identifier;

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(String.format("%s not found with id: %s", resourceName, identifier));
        this.resourceName = resourceName;
        this.identifier = identifier;
    }

    public String getResourceName() {
        return resourceName;
    }

    public Object getIdentifier() {
        return identifier;
    }
}
