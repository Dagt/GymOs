package com.gymos.notifications.api.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.gymos.notifications.api.EmailDeliveryPort;
import com.gymos.notifications.application.NotificationProperties;
import com.gymos.notifications.infrastructure.NotificationBrandRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PasswordRecoveryEmailDeliveryAdapterTests {
    private final EmailDeliveryPort smtp = mock(EmailDeliveryPort.class);
    private final NotificationBrandRepository brands = mock(NotificationBrandRepository.class);

    @Test void recoveryUsesConfiguredSmtpAndTenantBrandWithoutIncludingTokenInIdempotencyKey() {
        var email = email("staff@example.invalid");
        when(smtp.providerKey()).thenReturn("SMTP");
        when(smtp.available()).thenReturn(true);
        when(brands.emailBrand(email.organizationId())).thenReturn(new NotificationBrandRepository.EmailBrand("Gimnasio de prueba", null, "#123456", "#abcdef", "#ffffff"));
        assertThat(adapter(true).deliver(email)).isEqualTo("SENT");
        var message = ArgumentCaptor.forClass(EmailDeliveryPort.EmailMessage.class);
        verify(smtp).deliver(message.capture());
        assertThat(message.getValue().fromEmail()).isEqualTo("notificaciones@example.invalid");
        assertThat(message.getValue().recipient()).isEqualTo("staff@example.invalid");
        assertThat(message.getValue().brandName()).isEqualTo("Gimnasio de prueba");
        assertThat(message.getValue().actionUrl()).isEqualTo(email.url());
        assertThat(message.getValue().bodyText()).contains("un solo uso", "Ciudad de México");
        assertThat(message.getValue().idempotencyKey()).doesNotContain("private-token");
    }

    @Test void noEmailAndDisabledSmtpNeverPretendThatMailWasSent() {
        assertThat(adapter(false).deliver(email(null))).isEqualTo("NO_EMAIL");
        when(smtp.providerKey()).thenReturn("SMTP");
        assertThat(adapter(false).deliver(email("staff@example.invalid"))).isEqualTo("NOT_CONFIGURED");
        verify(smtp, never()).deliver(any());
        verifyNoInteractions(brands);
    }

    @Test void providerFailureIsSanitizedEvenWhenExceptionContainsThePrivateLink() {
        var email = email("staff@example.invalid");
        when(smtp.providerKey()).thenReturn("SMTP");
        when(smtp.available()).thenReturn(true);
        when(brands.emailBrand(any())).thenReturn(new NotificationBrandRepository.EmailBrand("Gimnasio", null, null, null, null));
        when(smtp.deliver(any())).thenThrow(new IllegalStateException("Provider failed while sending " + email.url()));
        assertThat(adapter(true).deliver(email)).isEqualTo("FAILED");
        assertThat(email.toString()).doesNotContain("private-token");
    }

    private PasswordRecoveryEmailDeliveryAdapter adapter(boolean enabled) {
        var config = new NotificationProperties.Smtp(enabled, "ZeptoMail", "smtp.example.invalid", 587,
                "test-user", "synthetic-test-secret", List.of("notificaciones@example.invalid"), true,
                true, true, false, true, 1000, 1000, 1000);
        return new PasswordRecoveryEmailDeliveryAdapter(List.of(smtp), new NotificationProperties(1000, 1000, 10, 60, 3, "https://test.example.invalid", "test", config), brands);
    }

    private PasswordRecoveryEmailDelivery.RecoveryEmail email(String recipient) {
        return new PasswordRecoveryEmailDelivery.RecoveryEmail(UUID.randomUUID(), UUID.randomUUID(), "Coach", recipient,
                "https://test.example.invalid/reset-password#token=private-token", OffsetDateTime.now().plusHours(1));
    }
}
