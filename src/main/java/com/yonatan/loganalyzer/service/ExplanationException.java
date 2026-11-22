package com.yonatan.loganalyzer.service;

/**
 * Thrown when we cannot get an explanation from the Claude API (missing key,
 * network problem, or an error reply). It has its own type so the API layer can
 * turn exactly this problem into a clear HTTP response.
 */
public class ExplanationException extends RuntimeException {

    public ExplanationException(String message) {
        super(message);
    }

    public ExplanationException(String message, Throwable cause) {
        super(message, cause);
    }
}
