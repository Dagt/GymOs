package com.gymos.selfservice.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.payments.api.PaymentConfigurationOperations;
import com.gymos.selfservice.application.result.SelfServiceCapabilities;
import com.gymos.selfservice.infrastructure.SelfServiceRepository;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.BranchKioskConfigurationData;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KioskSettingsService {
	private final SelfServiceRepository repository;
	private final SelfServiceCapabilityPolicy capabilityPolicy;
	private final PaymentConfigurationOperations paymentConfiguration;
	private final AuditService auditService;

	public KioskSettingsService(
			SelfServiceRepository repository,
			SelfServiceCapabilityPolicy capabilityPolicy,
			PaymentConfigurationOperations paymentConfiguration,
			AuditService auditService
	) {
		this.repository = repository;
		this.capabilityPolicy = capabilityPolicy;
		this.paymentConfiguration = paymentConfiguration;
		this.auditService = auditService;
	}

	@Transactional
	public List<BranchKioskSettings> configurations(GymOsPrincipal principal) {
		requireOrganization(principal);
		var payments = paymentConfiguration.configuration(principal.organizationId());
		return repository.branchConfigurations(principal.organizationId()).stream()
				.map(record -> new BranchKioskSettings(
						record.branchId(), record.branchName(), record.status(),
						capabilityPolicy.resolve(record.allowedActionsJson()).withPaymentReadiness(
								payments.stripeCheckoutReady(), payments.stripeRecurringReady()
						), record.updatedAt()
				))
				.toList();
	}

	@Transactional
	public BranchKioskSettings configure(
			GymOsPrincipal principal, UUID branchId, UpdateKioskSettings command, String correlationId
	) {
		requireOrganization(principal);
		if (!repository.configureBranch(
				principal.organizationId(), branchId, principal.userId(),
				new BranchKioskConfigurationData(
						command.clientRegistration(),
						command.membershipEnrollment(), command.visitPackagePurchase(), command.membershipRenewal(),
						command.membershipPlanChange(), command.resourceBooking(),
						command.wellnessBooking(), command.profileUpdate(), command.autopay()
				)
		)) {
			throw new BusinessException("KIOSK_BRANCH_NOT_FOUND", "La sucursal no existe o no esta activa.");
		}
		auditService.record(
				principal, "KIOSK_BRANCH_SETTINGS_UPDATED", "BRANCH", branchId,
				"BACKOFFICE", "Configuracion logica; no se asigna una computadora.", correlationId
		);
		return configurations(principal).stream()
				.filter(item -> item.branchId().equals(branchId))
				.findFirst()
				.orElseThrow(() -> new BusinessException("KIOSK_BRANCH_NOT_FOUND", "La sucursal no existe o no esta activa."));
	}

	private void requireOrganization(GymOsPrincipal principal) {
		if (principal == null || principal.organizationId() == null) {
			throw new BusinessException("ORGANIZATION_CONTEXT_REQUIRED", "Selecciona un gimnasio.");
		}
	}

	public record UpdateKioskSettings(
			boolean clientRegistration,
			boolean membershipEnrollment, boolean visitPackagePurchase, boolean membershipRenewal,
			boolean membershipPlanChange, boolean resourceBooking,
			boolean wellnessBooking, boolean profileUpdate, boolean autopay
	) {
		public UpdateKioskSettings(
				boolean clientRegistration, boolean membershipEnrollment, boolean membershipRenewal,
				boolean membershipPlanChange, boolean resourceBooking, boolean wellnessBooking,
				boolean profileUpdate, boolean autopay
		) {
			this(clientRegistration, membershipEnrollment, false, membershipRenewal,
					membershipPlanChange, resourceBooking, wellnessBooking, profileUpdate, autopay);
		}
	}

	public record BranchKioskSettings(
			UUID branchId, String branchName, String status,
			SelfServiceCapabilities capabilities, OffsetDateTime updatedAt
	) {}
}
