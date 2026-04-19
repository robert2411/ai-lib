package com.agentlibrary.service;

/**
 * Thrown when a requested artifact or version cannot be found.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
