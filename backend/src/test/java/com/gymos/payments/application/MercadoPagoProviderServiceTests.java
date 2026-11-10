package com.gymos.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.payments.api.MercadoPagoPointOperations.CreatePointOrderCommand;
import com.gymos.payments.api.PaymentConfigurationView;
import com.gymos.payments.application.port.CredentialCipher;
import com.gymos.payments.application.port.ProviderHttpGateway;
import com.gymos.payments.infrastructure.PaymentConfigurationRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import java.util.Map;
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

	@Test
	void pointRetryReusesTheProviderKeyAndDoesNotResendAnIdentifiedOrder() throws Exception {
		var json = new ObjectMapper();
		var http = mock(ProviderHttpGateway.class);
		var cipher = mock(CredentialCipher.class);
		var repository = mock(PaymentProviderRepository.class);
		var configurations = mock(PaymentConfigurationRepository.class);
		var configuration = mock(PaymentConfigurationView.class);
		var connection = mock(PaymentProviderRepository.ConnectionRecord.class);
		var operation = mock(PaymentProviderRepository.OperationRecord.class);
		UUID organization = UUID.randomUUID();
		UUID branch = UUID.randomUUID();
		UUID payment = UUID.randomUUID();
		UUID operationId = UUID.randomUUID();
		String key = "mp-point:" + payment;
		var command = new CreatePointOrderCommand(organization, branch, UUID.randomUUID(), payment, null, 50000, "MXN", key);
		when(configurations.configuration(organization, branch)).thenReturn(configuration);
		when(configuration.mercadoPagoReady()).thenReturn(true);
		when(configuration.mercadoPagoTerminalId()).thenReturn("terminal-fixture");
		when(repository.connection(organization, "MERCADO_PAGO")).thenReturn(Optional.of(connection));
		when(connection.accessTokenEncrypted()).thenReturn("encrypted-fixture");
		when(cipher.decrypt("encrypted-fixture")).thenReturn("point-fixture");
		when(repository.latestOperationForPayment(organization, "MERCADO_PAGO", "POINT_ORDER", payment))
				.thenReturn(Optional.empty(), Optional.of(operation));
		when(repository.createOperation(any())).thenReturn(operationId);
		when(repository.operation(operationId)).thenReturn(Optional.of(operation));
		when(operation.id()).thenReturn(operationId);
		when(operation.idempotencyKey()).thenReturn(key);
		when(operation.externalOperationId()).thenReturn(null, null, "order-fixture");
		var headers = Map.of("Authorization", "Bearer point-fixture", "X-Idempotency-Key", key);
		when(http.exchange(eq("POST"), eq("https://mp.test/v1/orders"), eq(headers), anyString(), eq("application/json")))
				.thenThrow(new IllegalStateException("Respuesta de Point perdida"))
				.thenReturn(json.readTree("{\"id\":\"order-fixture\"}"));
		var properties = new PaymentProviderProperties("key", "http://frontend.test",
				new PaymentProviderProperties.Stripe(null, null, null, null, null),
				new PaymentProviderProperties.MercadoPago("client", "secret", "http://redirect", "webhook", "https://mp.test", "https://auth.test"));
		var service = new MercadoPagoProviderService(properties, http, cipher, repository, configurations, json, mock(AuditService.class));

		assertThatThrownBy(() -> service.createOrder(command)).hasMessage("Respuesta de Point perdida");
		service.createOrder(command);
		service.createOrder(command);

		verify(repository).createOperation(any());
		verify(http, times(2)).exchange(eq("POST"), eq("https://mp.test/v1/orders"), eq(headers), anyString(), eq("application/json"));
	}
}
