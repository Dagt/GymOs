package com.gymos.payments.api;

public interface PaymentSecretResolver {
    String resolve(String reference);
}
