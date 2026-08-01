package com.gymos.payments.infrastructure;

import com.gymos.payments.api.OnlinePaymentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(OnlinePaymentGateway.class)
public class DisabledOnlinePaymentGateway implements OnlinePaymentGateway {
    @Override
    public String provider() {
        return "DISABLED";
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public CreateIntentResult createIntent(CreateIntentRequest request) {
        throw new IllegalStateException("Online payment provider is not configured");
    }

    @Override
    public WebhookResult verifyWebhook(String payload, String signature) {
        throw new IllegalStateException("Online payment provider is not configured");
    }
}
