package com.gymos.platformadmin.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.identity.api.operations.TenantOwnerProvisioning;
import com.gymos.identity.api.operations.TenantOwnerProvisioning.ProvisionOwner;
import com.gymos.organizations.api.operations.PlatformOrganizationOperations;
import com.gymos.organizations.api.operations.PlatformOrganizationOperations.ManagedOrganization;
import com.gymos.organizations.api.operations.PlatformOrganizationOperations.CreateManagedOrganization;
import com.gymos.platformadmin.infrastructure.PlatformCustomerAccountRepository;
import com.gymos.platformadmin.infrastructure.PlatformCustomerAccountRepository.CustomerAccountRecord;
import com.gymos.platformadmin.infrastructure.PlatformCustomerAccountRepository.CustomerAccountWrite;
import com.gymos.platformbilling.api.operations.PlatformBillingOperations;
import com.gymos.platformbilling.api.operations.PlatformBillingOperations.BranchCommercialStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

@Service
public class PlatformCustomerService {
	private static final Set<String> LIFECYCLE_STATUSES = Set.of(
			"ONBOARDING", "ACTIVE", "AT_RISK", "OFFBOARDING", "FORMER");
	private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Mexico_City");

	private final PlatformOrganizationOperations organizationOperations;
	private final PlatformCustomerAccountRepository accountRepository;
	private final PlatformBillingOperations billingOperations;
	private final AuditService auditService;
	private final TenantOwnerProvisioning ownerProvisioning;

	public PlatformCustomerService(
			PlatformOrganizationOperations organizationOperations,
			PlatformCustomerAccountRepository accountRepository,
			PlatformBillingOperations billingOperations,
			AuditService auditService,
			TenantOwnerProvisioning ownerProvisioning
	) {
		this.organizationOperations = organizationOperations;
		this.accountRepository = accountRepository;
		this.billingOperations = billingOperations;
		this.auditService = auditService;
		this.ownerProvisioning = ownerProvisioning;
	}

	@Transactional
	public ProvisionedCustomer create(
			GymOsPrincipal principal,
			CreateCustomerCommand command,
			String correlationId
	) {
		requirePlatformAdmin(principal);
		OffsetDateTime now = OffsetDateTime.now();
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		String normalizedEmail = command.ownerEmail().trim().toLowerCase(java.util.Locale.ROOT);
		try {
			organizationOperations.create(new CreateManagedOrganization(
					organizationId, branchId, principal.userId(), command.legalName().trim(),
					command.tradeName().trim(), blankToNull(command.taxId()), command.timezone().trim(),
					command.defaultCurrency().trim().toUpperCase(), command.contactEmail().trim().toLowerCase(),
					command.contactPhone().trim(), defaultText(command.brandName(), command.tradeName()),
					blankToNull(command.logoUrl()), defaultText(command.primaryColor(), "#093D3A"),
					defaultText(command.accentColor(), "#18C8B2"),
					defaultText(command.surfaceColor(), "#F2F8F7"), command.branchName().trim(),
					command.branchCode().trim().toUpperCase(), command.branchPhone().trim(), now));
			var invitation = ownerProvisioning.provisionOwner(new ProvisionOwner(
					organizationId, branchId, principal.userId(), command.ownerFirstName().trim(),
					command.ownerLastName().trim(), normalizedEmail, command.ownerPhone().trim(), now));
			auditService.recordSystem(
					organizationId, branchId, principal.userId(), "PLATFORM_CUSTOMER_PROVISIONED", "ORGANIZATION",
					organizationId, "PLATFORM_ADMIN", "ownerUserId=" + invitation.userId(), correlationId);
			ManagedOrganization organization = organizationOperations.organization(organizationId)
					.orElseThrow(() -> new BusinessException("ORGANIZATION_NOT_FOUND", "El gimnasio creado no esta disponible."));
			return new ProvisionedCustomer(
					toView(organization, null, BillingAggregate.empty()), normalizedEmail,
					invitation.invitationUrl(), invitation.expiresAt());
		} catch (DuplicateKeyException exception) {
			throw new BusinessException(
					"PLATFORM_CUSTOMER_ALREADY_EXISTS",
					"Ya existe un gimnasio, RFC, codigo de sucursal o correo de acceso con esos datos.");
		}
	}

	@Transactional(readOnly = true)
	public CustomerPortfolioView portfolio(GymOsPrincipal principal) {
		requirePlatformAdmin(principal);
		List<ManagedOrganization> organizations = organizationOperations.organizations();
		Map<UUID, CustomerAccountRecord> accounts = new LinkedHashMap<>();
		accountRepository.findAll().forEach(account -> accounts.put(account.organizationId(), account));
		Map<UUID, BillingAggregate> billingByOrganization = aggregateBilling(
				billingOperations.branches(principal));

		List<PlatformCustomerView> customers = organizations.stream()
				.map(organization -> toView(
						organization,
						accounts.get(organization.id()),
						billingByOrganization.getOrDefault(organization.id(), BillingAggregate.empty())))
				.sorted(Comparator.comparing(PlatformCustomerView::tradeName, String.CASE_INSENSITIVE_ORDER))
				.toList();
		return new CustomerPortfolioView(summary(customers), customers);
	}

	@Transactional
	public PlatformCustomerView update(
			GymOsPrincipal principal,
			UUID organizationId,
			UpdateCustomerAccountCommand command,
			String correlationId
	) {
		requirePlatformAdmin(principal);
		ManagedOrganization organization = organizationOperations.organization(organizationId)
				.orElseThrow(() -> new BusinessException(
						"ORGANIZATION_NOT_FOUND", "El gimnasio cliente no esta disponible."));
		String lifecycleStatus = normalizeLifecycle(command.lifecycleStatus());
		String accountOwner = bounded(command.accountOwner(), 120, "CUSTOMER_ACCOUNT_OWNER_TOO_LONG",
				"El responsable comercial no puede exceder 120 caracteres.");
		String billingEmail = bounded(command.billingEmail(), 254, "CUSTOMER_BILLING_EMAIL_TOO_LONG",
				"El correo de cobranza no puede exceder 254 caracteres.");
		if (billingEmail != null && (!billingEmail.contains("@") || billingEmail.startsWith("@")
				|| billingEmail.endsWith("@"))) {
			throw new BusinessException(
					"CUSTOMER_BILLING_EMAIL_INVALID", "Captura un correo de cobranza valido.");
		}
		String billingPhone = bounded(command.billingPhone(), 32, "CUSTOMER_BILLING_PHONE_TOO_LONG",
				"El telefono de cobranza no puede exceder 32 caracteres.");
		String notes = bounded(command.notes(), 2000, "CUSTOMER_ACCOUNT_NOTES_TOO_LONG",
				"Las notas comerciales no pueden exceder 2000 caracteres.");
		if (command.paymentTermsDays() < 0 || command.paymentTermsDays() > 90) {
			throw new BusinessException(
					"CUSTOMER_PAYMENT_TERMS_INVALID", "Los dias de credito deben estar entre 0 y 90.");
		}
		if (command.expectedVersion() < 0) {
			throw new BusinessException(
					"CUSTOMER_ACCOUNT_VERSION_INVALID", "La version del expediente comercial no es valida.");
		}
		if (command.renewalOn() != null && command.contractStartedOn() != null
				&& command.renewalOn().isBefore(command.contractStartedOn())) {
			throw new BusinessException(
					"CUSTOMER_RENEWAL_DATE_INVALID",
					"La renovacion no puede ser anterior al inicio del contrato.");
		}

		OffsetDateTime now = OffsetDateTime.now();
		int updated = accountRepository.upsert(new CustomerAccountWrite(
				organizationId, lifecycleStatus, accountOwner, billingEmail, billingPhone,
				command.contractStartedOn(), command.renewalOn(), command.paymentTermsDays(),
				command.nextFollowUpOn(), notes, command.expectedVersion(), principal.userId(), now));
		if (updated == 0) {
			throw new BusinessException(
					"CUSTOMER_ACCOUNT_VERSION_CONFLICT",
					"El expediente comercial cambio mientras lo editabas. Recarga antes de guardar.");
		}

		auditService.recordSystem(
				organizationId, null, principal.userId(), "PLATFORM_CUSTOMER_ACCOUNT_UPDATED",
				"ORGANIZATION", organizationId, "PLATFORM_ADMIN",
				"Expediente comercial actualizado", correlationId);
		CustomerAccountRecord persisted = accountRepository.find(organizationId)
				.orElseThrow(() -> new BusinessException(
						"CUSTOMER_ACCOUNT_NOT_FOUND", "El expediente comercial no esta disponible."));
		BillingAggregate billing = aggregateBilling(
				billingOperations.branches(principal)).getOrDefault(organizationId, BillingAggregate.empty());
		return toView(organization, persisted, billing);
	}

	private Map<UUID, BillingAggregate> aggregateBilling(List<BranchCommercialStatus> branches) {
		Map<UUID, BillingAggregateBuilder> builders = new LinkedHashMap<>();
		for (BranchCommercialStatus branch : branches) {
			BillingAggregateBuilder builder = builders.computeIfAbsent(
					branch.organizationId(), ignored -> new BillingAggregateBuilder());
			builder.activeMembers += branch.activeMembers();
			if ("ACTIVE".equals(branch.subscriptionStatus()) && branch.monthlyAmount() != null) {
				builder.monthlyRecurringRevenue = builder.monthlyRecurringRevenue.add(branch.monthlyAmount());
			}
			if ("OVERDUE".equals(branch.paymentStatus())) builder.overdueBranchCount++;
			if ("UNCONFIGURED".equals(branch.subscriptionStatus())) builder.unconfiguredBranchCount++;
			if (!branch.implementationPaid() && branch.implementationFee().signum() > 0) {
				builder.implementationOutstanding = builder.implementationOutstanding.add(branch.implementationFee());
			}
			if ("ACTIVE".equals(branch.subscriptionStatus()) && branch.nextBillingDate() != null
					&& (builder.nextBillingDate == null || branch.nextBillingDate().isBefore(builder.nextBillingDate))) {
				builder.nextBillingDate = branch.nextBillingDate();
			}
		}
		Map<UUID, BillingAggregate> result = new LinkedHashMap<>();
		builders.forEach((organizationId, builder) -> result.put(organizationId, builder.build()));
		return result;
	}

	private PlatformCustomerView toView(
			ManagedOrganization organization,
			CustomerAccountRecord account,
			BillingAggregate billing
	) {
		String lifecycleStatus = account == null ? "ONBOARDING" : account.lifecycleStatus();
		String billingEmail = account == null || account.billingEmail() == null
				? organization.contactEmail() : account.billingEmail();
		String billingPhone = account == null || account.billingPhone() == null
				? organization.contactPhone() : account.billingPhone();
		String healthStatus = healthStatus(organization, lifecycleStatus, billing);
		return new PlatformCustomerView(
				organization.id(), organization.tradeName(), organization.legalName(), organization.taxId(),
				organization.status(), organization.contactEmail(), organization.contactPhone(),
				organization.brandName(), organization.logoUrl(), organization.branchCount(),
				organization.activeBranchCount(), lifecycleStatus,
				account == null ? null : account.accountOwner(), billingEmail, billingPhone,
				account == null ? null : account.contractStartedOn(),
				account == null ? null : account.renewalOn(),
				account == null ? 0 : account.paymentTermsDays(),
				account == null ? null : account.nextFollowUpOn(),
				account == null ? null : account.notes(),
				account == null ? 0 : account.version(),
				account == null ? null : account.updatedAt(),
				billing.activeMembers(), billing.monthlyRecurringRevenue(), billing.overdueBranchCount(),
				billing.unconfiguredBranchCount(), billing.implementationOutstanding(),
				billing.nextBillingDate(), healthStatus);
	}

	private CustomerPortfolioSummary summary(List<PlatformCustomerView> customers) {
		LocalDate today = LocalDate.now(BUSINESS_ZONE);
		LocalDate followUpLimit = today.plusDays(7);
		BigDecimal mrr = BigDecimal.ZERO;
		int active = 0;
		int onboarding = 0;
		int atRisk = 0;
		int suspended = 0;
		int overdueBranches = 0;
		int followUpsDue = 0;
		for (PlatformCustomerView customer : customers) {
			mrr = mrr.add(customer.monthlyRecurringRevenue());
			if ("ACTIVE".equals(customer.lifecycleStatus())) active++;
			if ("ONBOARDING".equals(customer.lifecycleStatus())) onboarding++;
			if ("AT_RISK".equals(customer.healthStatus())) atRisk++;
			if (!"ACTIVE".equals(customer.organizationStatus())) suspended++;
			overdueBranches += customer.overdueBranchCount();
			if (customer.nextFollowUpOn() != null && !customer.nextFollowUpOn().isAfter(followUpLimit)
					&& !"FORMER".equals(customer.lifecycleStatus())) {
				followUpsDue++;
			}
		}
		return new CustomerPortfolioSummary(
				customers.size(), active, onboarding, atRisk, suspended, overdueBranches, followUpsDue, mrr);
	}

	private String healthStatus(
			ManagedOrganization organization,
			String lifecycleStatus,
			BillingAggregate billing
	) {
		if ("FORMER".equals(lifecycleStatus)) return "FORMER";
		if (!"ACTIVE".equals(organization.status())) return "SUSPENDED";
		if ("AT_RISK".equals(lifecycleStatus) || billing.overdueBranchCount() > 0) return "AT_RISK";
		if ("ONBOARDING".equals(lifecycleStatus) || organization.activeBranchCount() == 0
				|| billing.unconfiguredBranchCount() > 0) return "SETUP_REQUIRED";
		if ("OFFBOARDING".equals(lifecycleStatus)) return "OFFBOARDING";
		return "HEALTHY";
	}

	private String normalizeLifecycle(String value) {
		String normalized = value == null ? "" : value.trim().toUpperCase();
		if (!LIFECYCLE_STATUSES.contains(normalized)) {
			throw new BusinessException(
					"CUSTOMER_LIFECYCLE_INVALID", "Selecciona una etapa comercial valida.");
		}
		return normalized;
	}

	private String bounded(String value, int maxLength, String code, String message) {
		String normalized = value == null || value.isBlank() ? null : value.trim();
		if (normalized != null && normalized.length() > maxLength) {
			throw new BusinessException(code, message);
		}
		return normalized;
	}

	private String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String defaultText(String value, String fallback) {
		String normalized = blankToNull(value);
		return normalized == null ? fallback.trim() : normalized;
	}

	private void requirePlatformAdmin(GymOsPrincipal principal) {
		if (principal == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
		if (!principal.platformAdmin()) {
			throw new BusinessException(
					"PLATFORM_ACCESS_REQUIRED", "Solo Admin GymFort puede gestionar clientes de plataforma.");
		}
	}

	private record BillingAggregate(
			int activeMembers,
			BigDecimal monthlyRecurringRevenue,
			int overdueBranchCount,
			int unconfiguredBranchCount,
			BigDecimal implementationOutstanding,
			LocalDate nextBillingDate
	) {
		private static BillingAggregate empty() {
			return new BillingAggregate(0, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, null);
		}
	}

	private static final class BillingAggregateBuilder {
		private int activeMembers;
		private BigDecimal monthlyRecurringRevenue = BigDecimal.ZERO;
		private int overdueBranchCount;
		private int unconfiguredBranchCount;
		private BigDecimal implementationOutstanding = BigDecimal.ZERO;
		private LocalDate nextBillingDate;

		private BillingAggregate build() {
			return new BillingAggregate(
					activeMembers, monthlyRecurringRevenue, overdueBranchCount,
					unconfiguredBranchCount, implementationOutstanding, nextBillingDate);
		}
	}

	public record UpdateCustomerAccountCommand(
			String lifecycleStatus,
			String accountOwner,
			String billingEmail,
			String billingPhone,
			LocalDate contractStartedOn,
			LocalDate renewalOn,
			int paymentTermsDays,
			LocalDate nextFollowUpOn,
			String notes,
			long expectedVersion
	) {
	}

	public record CreateCustomerCommand(
			String legalName,
			String tradeName,
			String taxId,
			String timezone,
			String defaultCurrency,
			String contactEmail,
			String contactPhone,
			String brandName,
			String logoUrl,
			String primaryColor,
			String accentColor,
			String surfaceColor,
			String branchName,
			String branchCode,
			String branchPhone,
			String ownerFirstName,
			String ownerLastName,
			String ownerEmail,
			String ownerPhone
	) {
	}

	public record ProvisionedCustomer(
			PlatformCustomerView customer,
			String ownerEmail,
			String invitationUrl,
			OffsetDateTime invitationExpiresAt
	) {
	}

	public record CustomerPortfolioView(
			CustomerPortfolioSummary summary,
			List<PlatformCustomerView> customers
	) {
	}

	public record CustomerPortfolioSummary(
			int customerCount,
			int activeCustomerCount,
			int onboardingCustomerCount,
			int atRiskCustomerCount,
			int suspendedCustomerCount,
			int overdueBranchCount,
			int followUpsDueCount,
			BigDecimal monthlyRecurringRevenue
	) {
	}

	public record PlatformCustomerView(
			UUID organizationId,
			String tradeName,
			String legalName,
			String taxId,
			String organizationStatus,
			String contactEmail,
			String contactPhone,
			String brandName,
			String logoUrl,
			int branchCount,
			int activeBranchCount,
			String lifecycleStatus,
			String accountOwner,
			String billingEmail,
			String billingPhone,
			LocalDate contractStartedOn,
			LocalDate renewalOn,
			int paymentTermsDays,
			LocalDate nextFollowUpOn,
			String notes,
			long version,
			OffsetDateTime updatedAt,
			int activeMembers,
			BigDecimal monthlyRecurringRevenue,
			int overdueBranchCount,
			int unconfiguredBranchCount,
			BigDecimal implementationOutstanding,
			LocalDate nextBillingDate,
			String healthStatus
	) {
	}
}
