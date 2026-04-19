package com.agentlibrary.metadata;

import java.util.List;

/**
 * Exception thrown when metadata validation fails.
 * Contains a list of all validation errors found.
 */
public class ValidationException extends RuntimeException {

    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    /**
     * Returns the list of validation errors.
     */
    public List<String> errors() {
        return errors;
    }
}
