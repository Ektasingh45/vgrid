package com.vgrid.dao;

/**
 * The data could not be saved because it clashes with existing data, for example
 * a time slot that another driver booked a moment earlier, or an email that is already registered.
 * Extends IllegalArgumentException so forms can show the message like any other validation error.
 */
public class ConflictException extends IllegalArgumentException {
    public ConflictException(String message) {
        super(message);
    }
}
