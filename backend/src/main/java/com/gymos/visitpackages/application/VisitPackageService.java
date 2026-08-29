package com.gymos.visitpackages.application;

import com.gymos.audit.api.AuditService;
import com.gymos.catalog.api.VisitPackagePlanOperations;
import com.gymos.catalog.api.VisitPackagePlanOperations.VisitPackagePlanSnapshot;
import com.gymos.clients.api.operations.ClientLifecycleOperations;
import com.gymos.clients.api.operations.ClientLifecycleOperations.RegistrationFee;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.sales.api.operations.CreateVisitPackageSaleCommand;
import com.gymos.sales.api.operations.SalesOperations;
import com.gymos.sales.api.operations.VisitPackageSaleResult;
import com.gymos.visitpackages.api.operations.VisitPackageOperations;
import com.gymos.visitpackages.application.port.VisitPackageEntitlementPort;
import com.gymos.visitpackages.application.port.VisitPackageEntitlementPort.BenefitCoverage;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitPackageService implements VisitPackageOperations {
	private static final Duration PENDING_PAYMENT_TTL = Duration.ofMinutes(30);
	private final VisitPackagePlanOperations plans;
	private final VisitPackageEntitlementPort entitlements;
	private final ClientLifecycleOperations lifecycle;
	private final SalesOperations sales;
	private final VisitPackageActivationService activationService;
	private final AuditService auditService;

	public VisitPackageService(
			VisitPackagePlanOperations plans,
			VisitPackageEntitlementPort entitlements,
			ClientLifecycleOperations lifecycle,
			SalesOperations sales,
			VisitPackageActivationService activationService,
			AuditService auditService
	) {
		this.plans = plans;
		this.entitlements = entitlements;
		this.lifecycle = lifecycle;
		this.sales = sales;
		this.activationService = activationService;
		this.auditService = auditService;
	}

	@Override
	@Transactional
	public VisitPackageQuoteView quote(GymOsPrincipal principal, UUID clientId, UUID packagePlanId) {
		requireBranch(principal);
		if (!entitlements.lockActiveClient(principal.organizationId(), clientId)) {
			throw new BusinessException("CLIENT_NOT_ACTIVE", "El socio no existe o no está activo.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		var businessDate = entitlements.businessDate(principal.organizationId(), principal.branchId(), now);
		entitlements.expireDue(principal.organizationId(), clientId, businessDate, now);
		requireNoOtherOpenPackage(principal.organizationId(), clientId, businessDate);
		return quoteFor(principal, clientId, requiredPlan(principal, packagePlanId), businessDate);
	}

	@Override
	@Transactional
	public VisitPackagePurchaseResult purchase(
			GymOsPrincipal principal, PurchaseVisitPackageCommand command, String correlationId
	) {
		requireBranch(principal);
		String idempotencyKey = required(command.idempotencyKey(), "VISIT_PACKAGE_IDEMPOTENCY_REQUIRED");
		if (!entitlements.lockActiveClient(principal.organizationId(), command.clientId())) {
			throw new BusinessException("CLIENT_NOT_ACTIVE", "El socio no existe o no está activo.");
		}
		OffsetDateTime now = OffsetDateTime.now();
		var businessDate = entitlements.businessDate(principal.organizationId(), principal.branchId(), now);
		entitlements.expireDue(principal.organizationId(), command.clientId(), businessDate, now);
		VisitPackagePlanSnapshot plan = requiredPlan(principal, command.packagePlanId());
		VisitPackageQuoteView quote = quoteFor(principal, command.clientId(), plan, businessDate);
		Optional<VisitPackageEntitlementView> retry = entitlements.byIdempotencyKey(
				principal.organizationId(), command.clientId(), idempotencyKey);
		if (retry.isPresent() && !retry.get().packagePlanId().equals(command.packagePlanId())) {
			throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La llave ya fue usada para otro paquete.");
		}
		if (retry.isEmpty()) requireNoOtherOpenPackage(principal.organizationId(), command.clientId(), businessDate);

		VisitPackageSaleResult sale = sales.createVisitPackagePurchase(new CreateVisitPackageSaleCommand(
				principal.organizationId(), principal.branchId(), principal.userId(), command.clientId(),
				plan.id(), plan.productId(), plan.name(), quote.packagePriceCents(),
				quote.registrationFeeCents(), quote.registrationFeeLabel(), quote.totalCents(), quote.currency(),
				command.method(), command.reference(), command.validateNow(), command.notes(), idempotencyKey,
				correlationId));
		if (retry.isPresent()) {
			if (!retry.get().saleId().equals(sale.saleId())) {
				throw new BusinessException("IDEMPOTENCY_KEY_REUSED", "La llave ya corresponde a otra venta.");
			}
			return result(retry.get(), sale);
		}

		UUID entitlementId = entitlements.createPending(
				principal.organizationId(), principal.branchId(), command.clientId(), plan.id(), sale.saleId(),
				plan.name(), plan.totalVisits(), plan.validityDays(), now.plus(PENDING_PAYMENT_TTL),
				plan.branchIds(), plan.benefitCoverages().stream()
						.map(coverage -> new BenefitCoverage(
								coverage.benefitId(), coverage.usageMode(), coverage.branchIds()))
						.toList(), now);
		if (sale.paymentCompleted()) {
			activationService.activate(
					principal.organizationId(), principal.branchId(), sale.saleId(), now,
					"payment-completed:" + sale.saleId());
		}
		VisitPackageEntitlementView entitlement = entitlements.bySale(principal.organizationId(), sale.saleId())
				.orElseThrow(() -> new IllegalStateException("No fue posible recuperar el paquete creado."));
		auditService.record(
				principal, "VISIT_PACKAGE_PURCHASE_CREATED", "VISIT_PACKAGE_ENTITLEMENT", entitlementId,
				"RECEPTION", null, correlationId);
		return result(entitlement, sale);
	}

	@Override
	@Transactional
	public Optional<VisitPackageEntitlementView> current(GymOsPrincipal principal, UUID clientId) {
		requireBranch(principal);
		if (!entitlements.lockActiveClient(principal.organizationId(), clientId)) return Optional.empty();
		OffsetDateTime now = OffsetDateTime.now();
		var businessDate = entitlements.businessDate(principal.organizationId(), principal.branchId(), now);
		entitlements.expireDue(principal.organizationId(), clientId, businessDate, now);
		return entitlements.open(principal.organizationId(), clientId);
	}

	@Override
	@Transactional(readOnly = true)
	public java.util.List<VisitPackageEntitlementView> history(
			GymOsPrincipal principal, UUID clientId, int limit
	) {
		requireBranch(principal);
		return entitlements.history(principal.organizationId(), clientId, Math.max(1, Math.min(limit, 25)));
	}

	private VisitPackageQuoteView quoteFor(
			GymOsPrincipal principal, UUID clientId, VisitPackagePlanSnapshot plan,
			java.time.LocalDate businessDate
	) {
		RegistrationFee fee = "WAIVED".equals(plan.registrationFeePolicy())
				? RegistrationFee.none(plan.currency(), 0)
				: lifecycle.registrationFee(principal.organizationId(), clientId, businessDate);
		if (fee.due() && !plan.currency().equalsIgnoreCase(fee.currency())) {
			throw new BusinessException(
					"REGISTRATION_FEE_CURRENCY_MISMATCH",
					"La moneda de la inscripción debe coincidir con la moneda del paquete.");
		}
		long registrationFeeCents = fee.due() ? fee.amountCents() : 0;
		long total;
		try {
			total = Math.addExact(plan.priceCents(), registrationFeeCents);
		} catch (ArithmeticException exception) {
			throw new BusinessException("VISIT_PACKAGE_TOTAL_INVALID", "El total del paquete es inválido.");
		}
		return new VisitPackageQuoteView(
				clientId, plan.id(), plan.name(), plan.totalVisits(), plan.validityDays(), plan.priceCents(),
				registrationFeeCents, fee.due() ? fee.label() : null, total, plan.currency(),
				plan.branchIds(), plan.benefitIds());
	}

	private VisitPackagePlanSnapshot requiredPlan(GymOsPrincipal principal, UUID planId) {
		return plans.activePlan(principal.organizationId(), principal.branchId(), planId)
				.orElseThrow(() -> new BusinessException(
						"VISIT_PACKAGE_PLAN_NOT_AVAILABLE", "El paquete no está disponible en esta sucursal."));
	}

	private void requireNoOtherOpenPackage(UUID organizationId, UUID clientId, java.time.LocalDate businessDate) {
		if (entitlements.open(organizationId, clientId).isPresent()) {
			throw new BusinessException(
					"VISIT_PACKAGE_OPEN_CONFLICT", "El socio ya tiene un paquete pendiente o activo.");
		}
		if (entitlements.hasActiveLegacyPass(organizationId, clientId, businessDate)) {
			throw new BusinessException(
					"VISIT_PACKAGE_LEGACY_CONFLICT",
					"El socio tiene un paquete histórico activo. Regularízalo antes de vender otro.");
		}
	}

	private VisitPackagePurchaseResult result(VisitPackageEntitlementView entitlement, VisitPackageSaleResult sale) {
		return new VisitPackagePurchaseResult(
				entitlement.id(), sale.saleId(), sale.paymentId(), sale.saleNumber(), sale.saleStatus(),
				entitlement.status(), sale.totalCents(), sale.paidCents(), sale.balanceCents(), sale.currency());
	}

	private String required(String value, String code) {
		if (value == null || value.isBlank()) throw new BusinessException(code, "Completa la llave de la operación.");
		return value.trim();
	}

	private void requireBranch(GymOsPrincipal principal) {
		if (principal == null || principal.organizationId() == null || principal.branchId() == null) {
			throw new BusinessException("BRANCH_CONTEXT_REQUIRED", "Selecciona una sucursal.");
		}
	}
}
