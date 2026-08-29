package com.gymfort.connector.provider.hikcentral;

final class HikCentralApiException extends RuntimeException {
    private final boolean retryable;

    HikCentralApiException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    HikCentralApiException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    boolean retryable() {
        return retryable;
    }
}
