package com.gymfort.connector.provider;

public final class ProviderException extends RuntimeException {
    private final boolean retryable;

    public ProviderException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public ProviderException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
