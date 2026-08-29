package com.gymfort.connector.cloud;

public final class CloudApiException extends RuntimeException {
    private final int statusCode;

    public CloudApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean authenticationFailure() {
        return statusCode == 401 || statusCode == 403;
    }
}
