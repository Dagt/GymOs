package com.gymos.payments.infrastructure;

import com.gymos.common.api.BusinessException;
import com.gymos.payments.api.PaymentSecretResolver;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class EnvironmentPaymentSecretResolver implements PaymentSecretResolver {
    private final Environment environment;

    public EnvironmentPaymentSecretResolver(Environment environment) { this.environment = environment; }

    @Override
    public String resolve(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new BusinessException("PAYMENT_SECRET_REFERENCE_REQUIRED", "Falta la referencia del secreto de pagos.");
        }
        String key = reference.startsWith("env:") ? reference.substring(4) : reference;
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new BusinessException("PAYMENT_SECRET_NOT_CONFIGURED", "El secreto de pagos no está configurado.");
        }
        return value;
    }
}
