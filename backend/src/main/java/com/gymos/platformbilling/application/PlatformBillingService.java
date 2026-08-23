package com.gymos.platformbilling.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository.BillingUpdateRecord;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository.BranchBillingRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformBillingService {
    private static final Set<String> SUBSCRIPTION_STATUSES = Set.of("ACTIVE", "PENDING", "PAUSED", "CANCELLED");
    private static final Set<String> PLAN_CODES = Set.of("GYMFORT_150", "GYMFORT_500", "GYMFORT_1000", "GYMFORT_2000", "ENTERPRISE");
    private static final ZoneId BILLING_ZONE = ZoneId.of("America/Mexico_City");

    private final PlatformBillingRepository repository;
    private final AuditService auditService;

    public PlatformBillingService(PlatformBillingRepository repository, AuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public BillingOverview overview(GymOsPrincipal principal) {
        requirePlatformAdmin(principal);
        List<BranchBillingView> branches = repository.findAll().stream().map(this::toView).toList();
        return new BillingOverview(summary(branches), branches);
    }

    @Transactional
    public BranchBillingView update(GymOsPrincipal principal, UUID branchId, UpdateBillingCommand command, String correlationId) {
        requirePlatformAdmin(principal);
        String subscriptionStatus = normalizeSubscriptionStatus(command.subscriptionStatus());
        String contractedPlanCode = normalizePlanCode(command.contractedPlanCode());
        if (command.billingAnchorDay() < 1 || command.billingAnchorDay() > 31) {
            throw new BusinessException("BILLING_ANCHOR_DAY_INVALID", "El dia de corte debe estar entre 1 y 31.");
        }
        if (command.nextBillingDate() == null) {
            throw new BusinessException("NEXT_BILLING_DATE_REQUIRED", "Captura la proxima fecha de cobro.");
        }
        requireNonNegative(command.lastPaymentAmount(), "LAST_PAYMENT_AMOUNT_INVALID", "El ultimo pago no puede ser negativo.");
        requireNonNegative(command.monthlyPriceOverride(), "MONTHLY_PRICE_INVALID", "El precio mensual no puede ser negativo.");
        requireNonNegative(command.implementationFee(), "IMPLEMENTATION_FEE_INVALID", "La implementacion no puede ser negativa.");
        validateManualPrice(contractedPlanCode, command.monthlyPriceOverride());
        String notes = blankToNull(command.notes());
        if (notes != null && notes.length() > 1000) {
            throw new BusinessException("BILLING_NOTES_TOO_LONG", "Las notas no pueden exceder 1000 caracteres.");
        }

        OffsetDateTime now = OffsetDateTime.now();
        int updated = repository.upsert(new BillingUpdateRecord(
                branchId,
                subscriptionStatus,
                command.billingAnchorDay(),
                command.nextBillingDate(),
                command.lastPaymentDate(),
                command.lastPaymentAmount(),
                contractedPlanCode,
                "ENTERPRISE".equals(contractedPlanCode) ? command.monthlyPriceOverride() : null,
                command.implementationFee() == null ? BigDecimal.ZERO : command.implementationFee(),
                command.implementationPaid(),
                notes,
                principal.userId(),
                now
        ));
        if (updated == 0) {
            throw new BusinessException("BRANCH_NOT_FOUND", "La sucursal no esta disponible.");
        }

        BranchBillingRecord persisted = repository.findByBranchId(branchId)
                .orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "La sucursal no esta disponible."));
        auditService.recordSystem(
                persisted.organizationId(), branchId, principal.userId(),
                "PLATFORM_BILLING_UPDATED", "BRANCH", branchId, "PLATFORM_ADMIN",
                "Actualizacion de configuracion comercial GymFort", correlationId
        );
        return toView(persisted);
    }

    private BillingSummary summary(List<BranchBillingView> branches) {
        Set<UUID> organizations = new LinkedHashSet<>();
        int activeMembers = 0;
        int overdue = 0;
        int unconfigured = 0;
        BigDecimal mrr = BigDecimal.ZERO;
        for (BranchBillingView branch : branches) {
            organizations.add(branch.organizationId());
            activeMembers += branch.activeMembers();
            if ("OVERDUE".equals(branch.paymentStatus())) overdue++;
            if ("UNCONFIGURED".equals(branch.subscriptionStatus())) unconfigured++;
            if ("ACTIVE".equals(branch.subscriptionStatus()) && branch.monthlyAmount() != null) {
                mrr = mrr.add(branch.monthlyAmount());
            }
        }
        return new BillingSummary(organizations.size(), branches.size(), activeMembers, mrr, overdue, unconfigured);
    }

    private BranchBillingView toView(BranchBillingRecord record) {
        Plan suggested = planForMembers(record.activeMembers());
        boolean configured = record.subscriptionStatus() != null;
        String contractedCode = configured && record.planOverride() != null ? record.planOverride() : suggested.code();
        Plan contracted = planByCode(contractedCode);
        BigDecimal effectivePrice = "ENTERPRISE".equals(contracted.code()) ? record.monthlyPriceOverride() : contracted.monthlyPrice();
        String subscriptionStatus = configured ? record.subscriptionStatus() : "UNCONFIGURED";

        return new BranchBillingView(
                record.organizationId(), record.organizationName(), record.organizationStatus(),
                record.branchId(), record.branchName(), record.branchCode(), record.branchStatus(), record.activeMembers(),
                suggested.code(), suggested.name(), suggested.monthlyPrice(),
                contracted.code(), contracted.name(), effectivePrice,
                subscriptionStatus, paymentStatus(subscriptionStatus, record.nextBillingDate()),
                record.billingAnchorDay(), record.nextBillingDate(), record.lastPaymentDate(), record.lastPaymentAmount(),
                record.monthlyPriceOverride(), record.implementationFee(), record.implementationPaid(), record.notes()
        );
    }

    private String paymentStatus(String subscriptionStatus, LocalDate nextBillingDate) {
        if ("UNCONFIGURED".equals(subscriptionStatus)) return "UNCONFIGURED";
        if (!"ACTIVE".equals(subscriptionStatus)) return subscriptionStatus;
        if (nextBillingDate == null) return "UNCONFIGURED";
        LocalDate today = LocalDate.now(BILLING_ZONE);
        if (nextBillingDate.isBefore(today)) return "OVERDUE";
        if (nextBillingDate.isEqual(today)) return "DUE_TODAY";
        return "CURRENT";
    }

    private Plan planForMembers(int activeMembers) {
        if (activeMembers <= 150) return planByCode("GYMFORT_150");
        if (activeMembers <= 500) return planByCode("GYMFORT_500");
        if (activeMembers <= 1000) return planByCode("GYMFORT_1000");
        if (activeMembers <= 2000) return planByCode("GYMFORT_2000");
        return planByCode("ENTERPRISE");
    }

    private Plan planByCode(String code) {
        return switch (code) {
            case "GYMFORT_150" -> new Plan(code, "GymFort 150", new BigDecimal("1190.00"));
            case "GYMFORT_500" -> new Plan(code, "GymFort 500", new BigDecimal("1590.00"));
            case "GYMFORT_1000" -> new Plan(code, "GymFort 1000", new BigDecimal("2190.00"));
            case "GYMFORT_2000" -> new Plan(code, "GymFort 2000", new BigDecimal("2990.00"));
            case "ENTERPRISE" -> new Plan(code, "GymFort Enterprise", null);
            default -> throw new BusinessException("BILLING_PLAN_INVALID", "El plan comercial no es valido.");
        };
    }

    private void validateManualPrice(String planCode, BigDecimal monthlyPriceOverride) {
        if ("ENTERPRISE".equals(planCode)) {
            if (monthlyPriceOverride == null) {
                throw new BusinessException("ENTERPRISE_PRICE_REQUIRED", "Captura el precio mensual acordado para Enterprise.");
            }
            return;
        }
        if (monthlyPriceOverride != null) {
            throw new BusinessException("BILLING_PRICE_OVERRIDE_NOT_ALLOWED", "Los planes estandar usan su precio de lista; el precio manual se reserva para Enterprise.");
        }
    }

    private String normalizeSubscriptionStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!SUBSCRIPTION_STATUSES.contains(normalized)) {
            throw new BusinessException("BILLING_STATUS_INVALID", "El estado de suscripcion no es valido.");
        }
        return normalized;
    }

    private String normalizePlanCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!PLAN_CODES.contains(normalized)) {
            throw new BusinessException("BILLING_PLAN_INVALID", "Selecciona un plan comercial valido.");
        }
        return normalized;
    }

    private void requireNonNegative(BigDecimal value, String code, String message) {
        if (value != null && value.signum() < 0) throw new BusinessException(code, message);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requirePlatformAdmin(GymOsPrincipal principal) {
        if (principal == null) throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
        if (!principal.platformAdmin()) {
            throw new BusinessException("PLATFORM_ACCESS_REQUIRED", "Solo Admin GymFort puede consultar la facturacion comercial.");
        }
    }

    private record Plan(String code, String name, BigDecimal monthlyPrice) {}

    public record UpdateBillingCommand(
            String subscriptionStatus,
            int billingAnchorDay,
            LocalDate nextBillingDate,
            LocalDate lastPaymentDate,
            BigDecimal lastPaymentAmount,
            String contractedPlanCode,
            BigDecimal monthlyPriceOverride,
            BigDecimal implementationFee,
            boolean implementationPaid,
            String notes
    ) {}

    public record BillingOverview(BillingSummary summary, List<BranchBillingView> branches) {}

    public record BillingSummary(
            int customerCount,
            int branchCount,
            int activeMemberCount,
            BigDecimal monthlyRecurringRevenue,
            int overdueBranchCount,
            int unconfiguredBranchCount
    ) {}

    public record BranchBillingView(
            UUID organizationId,
            String organizationName,
            String organizationStatus,
            UUID branchId,
            String branchName,
            String branchCode,
            String branchStatus,
            int activeMembers,
            String suggestedPlanCode,
            String suggestedPlanName,
            BigDecimal suggestedMonthlyPrice,
            String contractedPlanCode,
            String contractedPlanName,
            BigDecimal monthlyAmount,
            String subscriptionStatus,
            String paymentStatus,
            Integer billingAnchorDay,
            LocalDate nextBillingDate,
            LocalDate lastPaymentDate,
            BigDecimal lastPaymentAmount,
            BigDecimal monthlyPriceOverride,
            BigDecimal implementationFee,
            boolean implementationPaid,
            String notes
    ) {}
}
