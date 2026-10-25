package com.vgrid.dao;

/** The database could not be reached or a query failed. Wraps the original SQLException. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
