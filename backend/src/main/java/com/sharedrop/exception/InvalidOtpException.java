package com.sharedrop.exception;

public class InvalidOtpException extends RuntimeException {

    private final int remainingAttempts;

    public InvalidOtpException(String message, int remainingAttempts) {
        super(message);
        this.remainingAttempts = remainingAttempts;
    }

    public InvalidOtpException(String message) {
        super(message);
        this.remainingAttempts = -1;
    }

    public int getRemainingAttempts() {
        return remainingAttempts;
    }
}
