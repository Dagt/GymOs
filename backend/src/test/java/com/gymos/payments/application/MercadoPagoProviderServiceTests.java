package com.gymos.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.payments.application.port.CredentialCipher;
import com.gymos.payments.application.port.ProviderHttpGateway;
import com.gymos.payments.infrastructure.PaymentConfigurationRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MercadoPagoProviderServiceTests {
	@Test
	void oauthEncryptsTokensAndDoesNotPersistThemInMetadataJson() throws Exception {
		ObjectMapper objectMapper = new ObjectMapper();
		ProviderHttpGateway http = mock(ProviderHttpGateway.class);
		CredentialCipher cipher = mock(CredentialCipher.class);
		PaymentProviderRepository providerRepository = mock(PaymentProviderRepository.class);
		UUID organizationId = UUID.randomUUID();
		UUID actorId = UUID.randomUUID();
		when(providerRepository.consumeOauthRequest(anyString(), eq("MERCADO_PAGO"), any())).thenReturn(Optional.of(
				new PaymentProviderRepository.OauthRequestRecord(organizationId, actorId, "encrypted-verifier")
		));
		when(cipher.decrypt("encrypted-verifier")).thenReturn("code-verifier");
		when(cipher.encrypt("access-secret")).thenReturn("encrypted-access");
		when(cipher.encrypt("refresh-secret")).thenReturn("encrypted-refresh");
		when(http.exchange(eq("POST"), eq("https://mp.test/oauth/token"), any(), anyString(), eq("application/json")))
				.thenReturn(objectMapper.readTree("{\"access_token\":\"access-secret\",\"refresh_token\":\"refresh-secret\",\"user_id\":1234,\"expires_in\":3600,\"scope\":\"offline_access\"}"));
		var properties = new PaymentProviderProperties(
				"key", "http://frontend.test",
				new PaymentProviderProperties.Stripe(null, null, null, null, null),
				new PaymentProviderProperties.MercadoPago("client", "secret", "http://redirect", "webhook", "https://mp.test", "https://auth.test")
		);
		var service = new MercadoPagoProviderService(
				properties, http, cipher, providerRepository, mock(PaymentConfigurationRepository.class),
				objectMapper, mock(AuditService.class)
		);

		String redirect = service.completeOauth("authorization-code", "state", "correlation");

		ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
		verify(providerRepository).saveMercadoPagoConnection(
				eq(organizationId), eq(actorId), eq("1234"), eq("encrypted-access"), eq("encrypted-refresh"), any(), metadata.capture()
		);
		assertThat(metadata.getValue()).doesNotContain("access-secret", "refresh-secret", "access_token", "refresh_token");
		assertThat(metadata.getValue()).contains("1234", "offline_access");
		assertThat(redirect).isEqualTo("http://frontend.test/settings/organization?payment=mercado-pago-connected");
	}
}
