package com.sharedrop.exception;

public class ShareExpiredException extends RuntimeException {

    public ShareExpiredException(String message) {
        super(message);
    }
}
