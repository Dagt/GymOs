package com.gymos.payments.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.payments.api.PaymentConfigurationView;
import com.gymos.payments.api.StripeRecurringOperations;
import com.gymos.payments.api.events.RecurringChargeFailedV1;
import com.gymos.payments.api.events.RecurringChargeSucceededV1;
import com.gymos.payments.api.events.RecurringMandateActivatedV1;
import com.gymos.payments.application.port.ProviderHttpGateway;
import com.gymos.payments.infrastructure.PaymentConfigurationRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository;
import com.gymos.payments.infrastructure.PaymentProviderRepository.CreateOperationData;
import com.gymos.payments.infrastructure.PaymentProviderRepository.OperationRecord;
import com.gymos.payments.infrastructure.RecurringBillingRepository;
import com.gymos.payments.infrastructure.RecurringBillingRepository.ChargeRecord;
import com.gymos.payments.infrastructure.RecurringBillingRepository.CreateMandateData;
import com.gymos.payments.infrastructure.RecurringBillingRepository.CustomerRecord;
import com.gymos.payments.infrastructure.RecurringBillingRepository.MandateRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StripeRecurringBillingService implements StripeRecurringOperations {
	public static final String TERMS_VERSION = "2026-08-03";
	public static final String AMOUNT_RULE = "CURRENT_MEMBERSHIP_RENEWAL_TOTAL";
	private static final String PROVIDER = "STRIPE";
	private static final Logger LOGGER = LoggerFactory.getLogger(StripeRecurringBillingService.class);
	private final PaymentProviderProperties properties;
	private final ProviderHttpGateway http;
	private final RecurringBillingRepository recurringRepository;
	private final PaymentProviderRepository providerRepository;
	private final PaymentConfigurationRepository configurationRepository;
	private final ObjectMapper objectMapper;
	private final AuditService auditService;
	private final ApplicationEventPublisher eventPublisher;

	public StripeRecurringBillingService(
			PaymentProviderProperties properties,
			ProviderHttpGateway http,
			RecurringBillingRepository recurringRepository,
			PaymentProviderRepository providerRepository,
			PaymentConfigurationRepository configurationRepository,
			ObjectMapper objectMapper,
			AuditService auditService,
			ApplicationEventPublisher eventPublisher
	) {
		this.properties = properties;
		this.http = http;
		this.recurringRepository = recurringRepository;
		this.providerRepository = providerRepository;
		this.configurationRepository = configurationRepository;
		this.objectMapper = objectMapper;
		this.auditService = auditService;
		this.eventPublisher = eventPublisher;
	}

	@Override
	public RecurringTerms terms(RecurringMembershipContext context) {
		validateContext(context);
		String consent = "Autorizo a GymOS y al gimnasio a realizar cargos automaticos en mi tarjeta "
				+ "para renovar la membresia " + safePlanName(context.planName()) + ". "
				+ "El importe de cada cargo sera el total vigente de renovacion del plan, despues de aplicar "
				+ "los beneficios que correspondan. El precio actual de referencia es "
				+ money(context.currentPriceCents(), context.currency()) + ". "
				+ "El siguiente intento esta previsto para " + context.nextChargeOn() + ". "
				+ "Puedo revocar esta autorizacion antes del siguiente cobro desde el kiosco o con el gimnasio.";
		return new RecurringTerms(
				TERMS_VERSION, consent, AMOUNT_RULE, context.currentPriceCents(),
				normalizeCurrency(context.currency()), context.nextChargeOn()
		);
	}

	@Override
	@Transactional
	public MandateSetupResult beginSetup(BeginMandateSetupCommand command) {
		if (command == null) {
			throw new BusinessException("RECURRING_SETUP_INVALID", "La solicitud de domiciliacion es obligatoria.");
		}
		RecurringMembershipContext context = command.context();
		validateContext(context);
		requireRecurringReady(context.organizationId());
		RecurringTerms terms = terms(context);
		if (!command.accepted() || !TERMS_VERSION.equals(command.termsVersion())) {
			throw new BusinessException(
					"RECURRING_CONSENT_REQUIRED",
					"Debes aceptar la version vigente de los terminos de domiciliacion."
			);
		}
		String idempotencyKey = required(command.idempotencyKey(), "RECURRING_IDEMPOTENCY_REQUIRED");
		if (idempotencyKey.length() > 150) {
			throw new BusinessException("RECURRING_IDEMPOTENCY_INVALID", "La llave de la solicitud es demasiado larga.");
		}
		MandateRecord existingMandate = recurringRepository.mandateByKey(
				context.organizationId(), idempotencyKey
		).orElse(null);
		if (existingMandate != null) {
			if (!existingMandate.branchId().equals(context.branchId())
					|| !existingMandate.clientId().equals(context.clientId())
					|| !existingMandate.membershipId().equals(context.membershipId())) {
				throw new BusinessException(
						"RECURRING_IDEMPOTENCY_CONTEXT_MISMATCH",
						"La llave de la solicitud ya fue utilizada en otra domiciliacion."
				);
			}
			return setupResult(existingMandate);
		}
		MandateRecord currentMandate = recurringRepository.currentMandate(
				context.organizationId(), context.branchId(), context.clientId(), context.membershipId()
		).orElse(null);
		if (currentMandate != null && Set.of("ACTIVE", "PAST_DUE", "PAUSED").contains(currentMandate.status())) {
			if (!command.replaceExisting()) {
				throw new BusinessException(
						"RECURRING_MANDATE_ALREADY_ACTIVE",
						"La membresia ya tiene una domiciliacion. Confirma que deseas sustituir la tarjeta."
				);
			}
		}

		CustomerRecord customer = ensureCustomer(context);
		UUID mandateId = recurringRepository.createPendingMandate(new CreateMandateData(
				context.organizationId(), context.branchId(), context.clientId(), context.membershipId(),
				context.planId(), customer.id(), terms.termsVersion(), terms.consentText(), terms.amountRule(),
				OffsetDateTime.now(ZoneOffset.UTC), safeOrigin(command.consentOrigin()), idempotencyKey,
				context.nextChargeOn()
		));
		MandateRecord mandate = recurringRepository.mandate(mandateId).orElseThrow();
		OperationRecord existingOperation = providerRepository.latestOperationForMandate(
				context.organizationId(), PROVIDER, "SETUP_SESSION", mandateId
		).orElse(null);
		UUID operationId = existingOperation == null
				? providerRepository.createOperation(new CreateOperationData(
						context.organizationId(), context.branchId(), PROVIDER, "SETUP_SESSION",
						null, null, context.clientId(), "stripe-setup:" + idempotencyKey,
						0, normalizeCurrency(context.currency()), mandateId, null
				))
				: existingOperation.id();
		OperationRecord operation = providerRepository.operation(operationId).orElseThrow();
		if (operation.externalOperationId() != null && operation.checkoutUrl() != null) {
			return setupResult(mandate, operation);
		}

		PaymentConfigurationView configuration = configurationRepository.configuration(context.organizationId());
		Map<String, Object> body = PaymentProviderEncoding.values();
		body.put("mode", "setup");
		body.put("customer", customer.externalCustomerId());
		body.put("currency", normalizeCurrency(context.currency()).toLowerCase());
		body.put("payment_method_types[0]", "card");
		body.put("setup_intent_data[usage]", "off_session");
		body.put("success_url", frontendUrl("/kiosk/checkout/result?status=autopay-success&session_id={CHECKOUT_SESSION_ID}"));
		body.put("cancel_url", frontendUrl("/kiosk/checkout/result?status=autopay-cancelled"));
		body.put("client_reference_id", context.membershipId());
		body.put("metadata[gymos_operation_id]", operationId);
		body.put("metadata[gymos_mandate_id]", mandateId);
		body.put("setup_intent_data[metadata][gymos_mandate_id]", mandateId);
		JsonNode response = http.exchange(
				"POST", stripeUrl("/v1/checkout/sessions"),
				stripeHeaders(configuration.stripeAccountId(), operation.idempotencyKey()),
				PaymentProviderEncoding.form(body), "application/x-www-form-urlencoded"
		);
		String sessionId = requiredText(response, "id", "STRIPE_SETUP_SESSION_INVALID");
		String checkoutUrl = requiredText(response, "url", "STRIPE_SETUP_SESSION_INVALID");
		OffsetDateTime expiresAt = response.hasNonNull("expires_at")
				? OffsetDateTime.ofInstant(
						java.time.Instant.ofEpochSecond(response.path("expires_at").asLong()), ZoneOffset.UTC
				)
				: null;
		providerRepository.markOperationCreated(
				operationId, sessionId, response.path("status").asText("open").toUpperCase(),
				checkoutUrl, json(response), expiresAt
		);
		auditService.recordSystem(
				context.organizationId(), context.branchId(), null,
				"RECURRING_MANDATE_SETUP_STARTED", "RECURRING_PAYMENT_MANDATE", mandateId,
				"SELF_SERVICE_KIOSK", terms.termsVersion(), command.correlationId()
		);
		return setupResult(mandate, providerRepository.operation(operationId).orElseThrow());
	}

	@Override
	public Optional<RecurringMandateView> mandate(
			UUID organizationId, UUID branchId, UUID clientId, UUID membershipId
	) {
		return recurringRepository.currentMandate(organizationId, branchId, clientId, membershipId)
				.map(this::toView);
	}

	@Override
	@Transactional
	public RecurringMandateView revoke(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID membershipId,
			String reason,
			String correlationId
	) {
		MandateRecord current = recurringRepository.currentMandate(
				organizationId, branchId, clientId, membershipId
		).orElseThrow(() -> new BusinessException(
				"RECURRING_MANDATE_NOT_FOUND", "No existe una domiciliacion activa para esta membresia."
		));
		String resolvedReason = required(reason, "RECURRING_REVOCATION_REASON_REQUIRED");
		MandateRecord revoked = recurringRepository.revokeCurrentMandate(
				organizationId, branchId, clientId, membershipId, trim(resolvedReason, 500)
		).orElseThrow(() -> new BusinessException(
				"RECURRING_MANDATE_NOT_FOUND", "La domiciliacion ya no esta disponible para cancelacion."
		));
		cancelAwaitingPayments(current, trim(resolvedReason, 500));
		if (current.externalPaymentMethodId() != null
				&& current.storedPaymentMethodId() != null
				&& !recurringRepository.paymentMethodUsedByAnotherCurrentMandate(
						current.storedPaymentMethodId(), current.id()
				)) {
			try {
				detachPaymentMethod(organizationId, current.externalPaymentMethodId(), current.id());
			} catch (RuntimeException exception) {
				LOGGER.warn("Stripe payment method detach failed after local revocation. mandateId={}", current.id(), exception);
			}
		}
		auditService.recordSystem(
				organizationId, branchId, null, "RECURRING_MANDATE_REVOKED",
				"RECURRING_PAYMENT_MANDATE", current.id(), "SELF_SERVICE_KIOSK",
				trim(resolvedReason, 500), correlationId
		);
		return toView(revoked);
	}

	@Transactional
	public MandateRecord completeSetup(OperationRecord operation) {
		if (operation == null || !"SETUP_SESSION".equals(operation.operationType()) || operation.mandateId() == null) {
			throw new BusinessException("STRIPE_SETUP_OPERATION_INVALID", "La sesion no corresponde a una domiciliacion.");
		}
		MandateRecord mandate = recurringRepository.mandate(operation.mandateId()).orElseThrow(() ->
				new BusinessException("RECURRING_MANDATE_NOT_FOUND", "La domiciliacion ya no existe."));
		if ("ACTIVE".equals(mandate.status())) {
			providerRepository.markOperationStatus(operation.id(), "COMPLETED", "{}", true);
			return mandate;
		}
		JsonNode session = fetchStripeObject(operation.organizationId(), "/v1/checkout/sessions/" + operation.externalOperationId());
		if (!"setup".equalsIgnoreCase(session.path("mode").asText())
				|| !"complete".equalsIgnoreCase(session.path("status").asText())) {
			throw new BusinessException("STRIPE_SETUP_INCOMPLETE", "Stripe aun no completo la tokenizacion de la tarjeta.");
		}
		String setupIntentId = session.path("setup_intent").asText();
		if (setupIntentId.isBlank()) {
			throw new BusinessException("STRIPE_SETUP_INTENT_MISSING", "Stripe no devolvio la autorizacion de la tarjeta.");
		}
		JsonNode setupIntent = fetchStripeObject(operation.organizationId(), "/v1/setup_intents/" + setupIntentId);
		if (!"succeeded".equalsIgnoreCase(setupIntent.path("status").asText())) {
			throw new BusinessException("STRIPE_SETUP_INCOMPLETE", "La autorizacion de tarjeta no fue completada.");
		}
		if (!mandate.externalCustomerId().equals(setupIntent.path("customer").asText())) {
			throw new BusinessException("STRIPE_SETUP_CUSTOMER_MISMATCH", "La tarjeta no corresponde al socio autorizado.");
		}
		String paymentMethodId = setupIntent.path("payment_method").asText();
		if (paymentMethodId.isBlank()) {
			throw new BusinessException("STRIPE_PAYMENT_METHOD_MISSING", "Stripe no devolvio el metodo tokenizado.");
		}
		if (!"PENDING_SETUP".equals(mandate.status())) {
			try {
				detachPaymentMethod(operation.organizationId(), paymentMethodId, mandate.id());
			} catch (RuntimeException exception) {
				LOGGER.warn("Stripe payment method detach failed for an inactive mandate. mandateId={}", mandate.id(), exception);
			}
			providerRepository.markOperationStatus(operation.id(), "CANCELLED", json(session), true);
			return mandate;
		}
		JsonNode paymentMethod = fetchStripeObject(operation.organizationId(), "/v1/payment_methods/" + paymentMethodId);
		JsonNode card = paymentMethod.path("card");
		MandateRecord activated = recurringRepository.activateMandate(
				mandate.id(), paymentMethodId, paymentMethod.path("type").asText("card"),
				text(card, "brand"), text(card, "last4"), integer(card, "exp_month"), integer(card, "exp_year")
		);
		providerRepository.markOperationStatus(operation.id(), "COMPLETED", json(session), true);
		eventPublisher.publishEvent(new RecurringMandateActivatedV1(
				"recurring-mandate-activated:" + mandate.id(), mandate.organizationId(), mandate.branchId(),
				mandate.clientId(), mandate.membershipId(), mandate.id(), mandate.planId(),
				mandate.nextChargeOn(), OffsetDateTime.now(ZoneOffset.UTC)
		));
		auditService.recordSystem(
				mandate.organizationId(), mandate.branchId(), null, "RECURRING_MANDATE_ACTIVATED",
				"RECURRING_PAYMENT_MANDATE", mandate.id(), "STRIPE_WEBHOOK", null,
				"stripe-setup:" + operation.id()
		);
		return activated;
	}

	public OperationRecord startRecurringPayment(ChargeRecord charge) {
		if (charge == null || charge.saleId() == null || charge.paymentId() == null
				|| charge.amountCents() == null || charge.amountCents() <= 0) {
			throw new BusinessException("RECURRING_CHARGE_INVALID", "El cargo recurrente no tiene una venta pendiente valida.");
		}
		requireRecurringReady(charge.organizationId());
		String key = "stripe-recurring:" + charge.id() + ":attempt:" + charge.attemptNumber();
		OperationRecord latest = providerRepository.latestOperationForRecurringCharge(
				charge.organizationId(), charge.id()
		).orElse(null);
		OperationRecord existing = latest != null
				&& (latest.idempotencyKey().equals(key)
				|| (latest.externalOperationId() == null && !"FAILED".equals(latest.status())))
				? latest : null;
		UUID operationId = existing == null
				? providerRepository.createOperation(new CreateOperationData(
						charge.organizationId(), charge.branchId(), PROVIDER, "RECURRING_PAYMENT_INTENT",
						charge.saleId(), charge.paymentId(), charge.clientId(), key,
						charge.amountCents(), normalizeCurrency(charge.currency()), charge.mandateId(), charge.id()
				))
				: existing.id();
		OperationRecord operation = providerRepository.operation(operationId).orElseThrow();
		String accountId = configurationRepository.configuration(charge.organizationId()).stripeAccountId();
		if (operation.externalOperationId() == null) {
			Map<String, Object> body = PaymentProviderEncoding.values();
			body.put("amount", charge.amountCents());
			body.put("currency", normalizeCurrency(charge.currency()).toLowerCase());
			body.put("customer", charge.externalCustomerId());
			body.put("payment_method", charge.externalPaymentMethodId());
			body.put("description", "Renovacion automatica de membresia GymOS");
			body.put("metadata[gymos_operation_id]", operationId);
			body.put("metadata[gymos_mandate_id]", charge.mandateId());
			body.put("metadata[gymos_recurring_charge_id]", charge.id());
			body.put("metadata[gymos_sale_id]", charge.saleId());
			JsonNode created = http.exchange(
					"POST", stripeUrl("/v1/payment_intents"), stripeHeaders(accountId, operation.idempotencyKey()),
					PaymentProviderEncoding.form(body), "application/x-www-form-urlencoded"
			);
			String paymentIntentId = requiredText(created, "id", "STRIPE_PAYMENT_INTENT_INVALID");
			providerRepository.markOperationCreated(
					operationId, paymentIntentId, created.path("status").asText("requires_confirmation").toUpperCase(),
					null, json(created), null
			);
			operation = providerRepository.operation(operationId).orElseThrow();
		}
		return confirmRecurringPayment(operation);
	}

	public JsonNode fetchPaymentIntent(OperationRecord operation) {
		return fetchStripeObject(
				operation.organizationId(),
				"/v1/payment_intents/" + operation.externalOperationId() + "?expand[]=latest_charge"
		);
	}

	public OperationRecord confirmRecurringPayment(OperationRecord operation) {
		String accountId = configurationRepository.configuration(operation.organizationId()).stripeAccountId();
		Map<String, Object> confirmation = PaymentProviderEncoding.values();
		confirmation.put("off_session", true);
		JsonNode confirmed = http.exchange(
				"POST", stripeUrl("/v1/payment_intents/" + operation.externalOperationId() + "/confirm"),
				stripeHeaders(accountId, operation.idempotencyKey() + ":confirm"),
				PaymentProviderEncoding.form(confirmation),
				"application/x-www-form-urlencoded"
		);
		providerRepository.markOperationStatus(
				operation.id(), confirmed.path("status").asText("PROCESSING").toUpperCase(), json(confirmed), false
		);
		return providerRepository.operation(operation.id()).orElseThrow();
	}

	public void reconcileSetup(OperationRecord operation) {
		JsonNode session = fetchStripeObject(
				operation.organizationId(), "/v1/checkout/sessions/" + operation.externalOperationId()
		);
		String status = session.path("status").asText("open");
		if ("complete".equalsIgnoreCase(status)) {
			completeSetup(operation);
			return;
		}
		providerRepository.markOperationStatus(
				operation.id(), status.toUpperCase(), json(session), "expired".equalsIgnoreCase(status)
		);
	}

	@Transactional
	public Optional<ChargeRecord> completeRecurringCharge(OperationRecord operation, String payload) {
		Optional<ChargeRecord> completed = recurringRepository.completeChargeForOperation(operation.id());
		completed.ifPresent(charge -> eventPublisher.publishEvent(new RecurringChargeSucceededV1(
				"recurring-charge-succeeded:" + charge.id(), charge.organizationId(), charge.branchId(),
				charge.clientId(), charge.membershipId(), charge.mandateId(), charge.id(), charge.planId(),
				charge.saleId(), charge.amountCents(), charge.currency(), charge.dueOn(),
				OffsetDateTime.now(ZoneOffset.UTC)
		)));
		return completed;
	}

	@Transactional
	public Optional<ChargeRecord> completeZeroAmountCharge(UUID chargeId) {
		Optional<ChargeRecord> completed = recurringRepository.completeChargeWithoutProvider(chargeId);
		completed.ifPresent(charge -> eventPublisher.publishEvent(new RecurringChargeSucceededV1(
				"recurring-charge-succeeded:" + charge.id(), charge.organizationId(), charge.branchId(),
				charge.clientId(), charge.membershipId(), charge.mandateId(), charge.id(), charge.planId(),
				charge.saleId(), 0, charge.currency(), charge.dueOn(), OffsetDateTime.now(ZoneOffset.UTC)
		)));
		return completed;
	}

	@Transactional
	public Optional<ChargeRecord> failRecurringCharge(OperationRecord operation, String errorCode) {
		Optional<ChargeRecord> existing = recurringRepository.chargeForOperation(operation.id());
		if (existing.isEmpty() || Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(existing.get().status())) {
			return Optional.empty();
		}
		ChargeRecord failed = recurringRepository.failCharge(
				existing.get().id(), trim(errorCode, 500), 3,
				OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)
		);
		providerRepository.markOperationStatus(operation.id(), "FAILED", "{}", false);
		boolean exhausted = "FAILED".equals(failed.status());
		eventPublisher.publishEvent(new RecurringChargeFailedV1(
				"recurring-charge-failed:" + failed.id() + ":" + failed.attemptNumber(),
				failed.organizationId(), failed.branchId(), failed.clientId(), failed.membershipId(),
				failed.mandateId(), failed.id(), failed.planId(), failed.saleId(), failed.dueOn(),
				failed.attemptNumber(), exhausted, trim(errorCode, 120), OffsetDateTime.now(ZoneOffset.UTC)
		));
		return Optional.of(failed);
	}

	@Transactional
	public void revokeForPlanChange(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			UUID membershipId,
			String correlationId
	) {
		MandateRecord current = recurringRepository.currentMandate(
				organizationId, branchId, clientId, membershipId
		).orElse(null);
		if (current == null || !Set.of("PENDING_SETUP", "ACTIVE", "PAST_DUE", "PAUSED").contains(current.status())) {
			return;
		}
		String reason = "La membresia cambio de plan; se requiere un nuevo consentimiento.";
		recurringRepository.revokeAfterPlanChange(organizationId, membershipId);
		cancelAwaitingPayments(current, reason);
		if (current.externalPaymentMethodId() != null
				&& current.storedPaymentMethodId() != null
				&& !recurringRepository.paymentMethodUsedByAnotherCurrentMandate(
						current.storedPaymentMethodId(), current.id()
				)) {
			try {
				detachPaymentMethod(organizationId, current.externalPaymentMethodId(), current.id());
			} catch (RuntimeException exception) {
				LOGGER.warn("Stripe payment method detach failed after plan change. mandateId={}", current.id(), exception);
			}
		}
		auditService.recordSystem(
				organizationId, branchId, null, "RECURRING_MANDATE_REVOKED",
				"RECURRING_PAYMENT_MANDATE", current.id(), "MEMBERSHIP_PLAN_CHANGE",
				reason, correlationId
		);
	}

	private void cancelAwaitingPayments(MandateRecord mandate, String reason) {
		String accountId = configurationRepository.configuration(mandate.organizationId()).stripeAccountId();
		for (ChargeRecord charge : recurringRepository.awaitingProviderCharges(mandate.id())) {
			OperationRecord operation = providerRepository.operation(charge.providerOperationId()).orElse(null);
			if (operation == null || operation.externalOperationId() == null) continue;
			try {
				JsonNode cancelled = http.exchange(
						"POST", stripeUrl("/v1/payment_intents/" + operation.externalOperationId() + "/cancel"),
						stripeHeaders(accountId, "stripe-cancel:" + operation.id()), "",
						"application/x-www-form-urlencoded"
				);
				if ("canceled".equalsIgnoreCase(cancelled.path("status").asText())) {
					recurringRepository.cancelAwaitingCharge(charge.id(), reason);
					providerRepository.markOperationStatus(operation.id(), "CANCELLED", json(cancelled), true);
				}
			} catch (RuntimeException exception) {
				LOGGER.info(
						"Recurring payment could not be cancelled and will be reconciled. mandateId={} operationId={}",
						mandate.id(), operation.id(), exception
				);
			}
		}
	}

	private void detachPaymentMethod(UUID organizationId, String paymentMethodId, UUID mandateId) {
		String accountId = configurationRepository.configuration(organizationId).stripeAccountId();
		http.exchange(
				"POST", stripeUrl("/v1/payment_methods/" + paymentMethodId + "/detach"),
				stripeHeaders(accountId, "stripe-detach:" + mandateId), "",
				"application/x-www-form-urlencoded"
		);
	}

	private CustomerRecord ensureCustomer(RecurringMembershipContext context) {
		CustomerRecord customer = recurringRepository.reserveCustomer(context.organizationId(), context.clientId());
		if (customer.externalCustomerId() != null && !customer.externalCustomerId().isBlank()) return customer;
		String accountId = configurationRepository.configuration(context.organizationId()).stripeAccountId();
		Map<String, Object> body = PaymentProviderEncoding.values();
		body.put("description", "Socio GymOS " + context.clientId());
		body.put("metadata[gymos_organization_id]", context.organizationId());
		body.put("metadata[gymos_client_id]", context.clientId());
		JsonNode response = http.exchange(
				"POST", stripeUrl("/v1/customers"),
				stripeHeaders(accountId, "stripe-customer:" + context.organizationId() + ":" + context.clientId()),
				PaymentProviderEncoding.form(body), "application/x-www-form-urlencoded"
		);
		recurringRepository.attachExternalCustomer(
				customer.id(), requiredText(response, "id", "STRIPE_CUSTOMER_INVALID")
		);
		return recurringRepository.customer(customer.id()).orElseThrow();
	}

	private MandateSetupResult setupResult(MandateRecord mandate) {
		OperationRecord operation = providerRepository.latestOperationForMandate(
				mandate.organizationId(), PROVIDER, "SETUP_SESSION", mandate.id()
		).orElse(null);
		return setupResult(mandate, operation);
	}

	private MandateSetupResult setupResult(MandateRecord mandate, OperationRecord operation) {
		return new MandateSetupResult(
				mandate.id(), mandate.status(), operation == null ? null : operation.id(),
				operation == null ? null : operation.externalOperationId(),
				operation == null ? null : operation.checkoutUrl(), mandate.termsVersion(), mandate.nextChargeOn()
		);
	}

	private RecurringMandateView toView(MandateRecord mandate) {
		boolean canReplace = Set.of("ACTIVE", "PAST_DUE", "PAUSED").contains(mandate.status());
		String recoveryMessage = switch (mandate.status()) {
			case "PAST_DUE" -> "El ultimo cargo no se completo. Puedes registrar otra tarjeta y reanudar los siguientes intentos.";
			case "PAUSED" -> "La domiciliacion esta pausada. Sustituye la tarjeta o solicita apoyo en recepcion.";
			default -> null;
		};
		return new RecurringMandateView(
				mandate.id(), mandate.membershipId(), mandate.planId(), mandate.status(), mandate.provider(),
				mandate.brand(), mandate.lastFour(), mandate.expiresMonth(), mandate.expiresYear(),
				mandate.termsVersion(), mandate.amountRule(), mandate.consentedAt(), mandate.nextChargeOn(),
				mandate.consecutiveFailures(), mandate.lastError(), mandate.revokedAt(),
				canReplace, recoveryMessage
		);
	}

	private void requireRecurringReady(UUID organizationId) {
		requireConfigured();
		PaymentConfigurationView configuration = configurationRepository.configuration(organizationId);
		if (!configuration.stripeRecurringReady() || configuration.stripeAccountId() == null) {
			throw new BusinessException(
					"STRIPE_RECURRING_DISABLED",
					"La domiciliacion con Stripe no esta habilitada para este gimnasio."
			);
		}
	}

	private void validateContext(RecurringMembershipContext context) {
		if (context == null || context.organizationId() == null || context.branchId() == null
				|| context.clientId() == null || context.membershipId() == null || context.planId() == null
				|| context.nextChargeOn() == null || context.currentPriceCents() <= 0) {
			throw new BusinessException("RECURRING_MEMBERSHIP_INVALID", "La membresia no es valida para domiciliacion.");
		}
	}

	private JsonNode fetchStripeObject(UUID organizationId, String path) {
		String accountId = configurationRepository.configuration(organizationId).stripeAccountId();
		if (accountId == null || accountId.isBlank()) {
			throw new BusinessException("STRIPE_ACCOUNT_MISSING", "La cuenta conectada de Stripe no esta disponible.");
		}
		return http.exchange("GET", stripeUrl(path), stripeHeaders(accountId, null), null, null);
	}

	private Map<String, String> stripeHeaders(String accountId, String idempotencyKey) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + properties.stripe().secretKey().trim());
		if (accountId != null && !accountId.isBlank()) headers.put("Stripe-Account", accountId);
		if (idempotencyKey != null && !idempotencyKey.isBlank()) headers.put("Idempotency-Key", idempotencyKey);
		return headers;
	}

	private String frontendUrl(String path) {
		return properties.frontendBaseUrl().replaceAll("/+$", "") + path;
	}

	private String stripeUrl(String path) {
		return properties.stripe().apiBaseUrl().replaceAll("/+$", "") + path;
	}

	private void requireConfigured() {
		if (properties.stripe().secretKey() == null || properties.stripe().secretKey().isBlank()) {
			throw new BusinessException("STRIPE_NOT_CONFIGURED", "Configura la credencial de plataforma de Stripe.");
		}
	}

	private String required(String value, String code) {
		if (value == null || value.isBlank()) throw new BusinessException(code, "El valor es obligatorio.");
		return value.trim();
	}

	private String requiredText(JsonNode node, String field, String code) {
		String value = text(node, field);
		if (value == null || value.isBlank()) throw new BusinessException(code, "Stripe devolvio una respuesta incompleta.");
		return value;
	}

	private String text(JsonNode node, String field) {
		return node != null && node.hasNonNull(field) ? node.path(field).asText() : null;
	}

	private Integer integer(JsonNode node, String field) {
		return node != null && node.hasNonNull(field) && node.path(field).canConvertToInt()
				? node.path(field).intValue() : null;
	}

	private String normalizeCurrency(String currency) {
		return currency == null || currency.isBlank() ? "MXN" : currency.trim().toUpperCase();
	}

	private String safePlanName(String value) {
		return value == null || value.isBlank() ? "seleccionada" : value.trim();
	}

	private String safeOrigin(String value) {
		return value == null || value.isBlank() ? "SELF_SERVICE_KIOSK" : trim(value.trim().toUpperCase(), 40);
	}

	private String money(long cents, String currency) {
		return String.format(java.util.Locale.ROOT, "%.2f %s", cents / 100.0, normalizeCurrency(currency));
	}

	private String trim(String value, int maximum) {
		if (value == null) return null;
		return value.substring(0, Math.min(value.length(), maximum));
	}

	private String json(JsonNode node) {
		try {
			return objectMapper.writeValueAsString(node);
		} catch (Exception exception) {
			throw new IllegalStateException("No fue posible serializar la respuesta de Stripe.", exception);
		}
	}
}
