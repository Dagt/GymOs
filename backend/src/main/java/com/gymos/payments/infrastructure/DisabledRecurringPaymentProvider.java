package com.gymos.payments.infrastructure;

import com.gymos.payments.api.RecurringPaymentProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(RecurringPaymentProvider.class)
public class DisabledRecurringPaymentProvider implements RecurringPaymentProvider {
    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String provider() {
        return "DISABLED";
    }

    @Override
    public SetupResult createSetup(SetupRequest request) {
        throw new IllegalStateException("Recurring payment provider is not configured");
    }

    @Override
    public void revoke(String providerPaymentMethodId) {
        throw new IllegalStateException("Recurring payment provider is not configured");
    }
}
