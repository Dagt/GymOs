package com.gymfort.connector.provider;

public final class ProviderActuationException extends ProviderException {
    private final boolean outcomeUncertain;

    public ProviderActuationException(String message, boolean retryable, boolean outcomeUncertain) {
        super(message, retryable);
        this.outcomeUncertain = outcomeUncertain;
    }

    public ProviderActuationException(
            String message, boolean retryable, boolean outcomeUncertain, Throwable cause
    ) {
        super(message, retryable, cause);
        this.outcomeUncertain = outcomeUncertain;
    }

    public boolean outcomeUncertain() {
        return outcomeUncertain;
    }
}
