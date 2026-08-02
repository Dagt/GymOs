package com.gymos.selfservice.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.common.api.BusinessException;
import com.gymos.payments.api.OnlinePaymentGateway;
import com.gymos.payments.api.RecurringPaymentProvider;
import com.gymos.selfservice.application.result.SelfServiceCapabilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SelfServiceCapabilityPolicy {
	private final ObjectMapper objectMapper;
	private final OnlinePaymentGateway paymentGateway;
	private final RecurringPaymentProvider recurringPaymentProvider;

	public SelfServiceCapabilityPolicy(ObjectMapper objectMapper) {
		this(objectMapper, null, null);
	}

	@Autowired
	public SelfServiceCapabilityPolicy(
			ObjectMapper objectMapper,
			OnlinePaymentGateway paymentGateway,
			RecurringPaymentProvider recurringPaymentProvider
	) {
		this.objectMapper = objectMapper;
		this.paymentGateway = paymentGateway;
		this.recurringPaymentProvider = recurringPaymentProvider;
	}

	public SelfServiceCapabilities resolve(String allowedActionsJson) {
		JsonNode actions = parse(allowedActionsJson);
		boolean onlinePaymentsReady = paymentGateway != null && paymentGateway.available();
		boolean recurringPaymentsReady = recurringPaymentProvider != null && recurringPaymentProvider.available();
		return new SelfServiceCapabilities(
				enabled(actions, "membershipRenewal", "renewal", "renew"),
				enabled(actions, "membershipPlanChange", "planChange", "changePlan"),
				enabled(actions, "resourceBooking", "booking", "bookings"),
				enabled(actions, "wellnessBooking", "wellness"),
				enabled(actions, "profileUpdate", "contactUpdate", "profile"),
				enabled(actions, "autopay", "recurringPayment", "domiciliation"),
				onlinePaymentsReady,
				recurringPaymentsReady
		);
	}

	public void require(SelfServiceCapabilities capabilities, SelfServiceCapability capability) {
		if (!capabilities.allows(capability)) {
			throw new BusinessException(
					"SELF_SERVICE_ACTION_NOT_ALLOWED",
					"Esta operacion no esta habilitada en este kiosco."
			);
		}
	}

	public void requireOnlineCardPayments(SelfServiceCapabilities capabilities) {
		if (!capabilities.onlineCardPaymentsReady()) {
			throw new BusinessException(
					"SELF_SERVICE_CARD_PAYMENTS_NOT_READY",
					"El pago con tarjeta aun no esta disponible en este kiosco."
			);
		}
	}

	private JsonNode parse(String value) {
		try {
			return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
		} catch (JsonProcessingException exception) {
			throw new BusinessException(
					"SELF_SERVICE_CAPABILITIES_INVALID",
					"La configuracion del kiosco no es valida."
			);
		}
	}

	private boolean enabled(JsonNode actions, String... names) {
		for (String name : names) {
			JsonNode value = actions.get(name);
			if (value != null && value.isBoolean()) {
				return value.asBoolean();
			}
		}
		return false;
	}
}
