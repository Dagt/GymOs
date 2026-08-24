package com.gymos.platformbilling.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository.BillingPaymentRecord;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository.BillingPaymentWrite;
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
    private static final Set<String> PAYMENT_TYPES = Set.of("SUBSCRIPTION", "IMPLEMENTATION", "OTHER");
    private static final Set<String> PAYMENT_METHODS = Set.of("TRANSFER", "CASH", "CARD", "STRIPE", "OTHER");
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
        if (command.expectedVersion() < 0) {
            throw new BusinessException("BILLING_VERSION_INVALID", "La version de la configuracion comercial no es valida.");
        }
        if (command.billingAnchorDay() < 1 || command.billingAnchorDay() > 31) {
            throw new BusinessException("BILLING_ANCHOR_DAY_INVALID", "El dia de corte debe estar entre 1 y 31.");
        }
        if (command.nextBillingDate() == null) {
            throw new BusinessException("NEXT_BILLING_DATE_REQUIRED", "Captura la proxima fecha de cobro.");
        }
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
                contractedPlanCode,
                "ENTERPRISE".equals(contractedPlanCode) ? command.monthlyPriceOverride() : null,
                command.implementationFee() == null ? BigDecimal.ZERO : command.implementationFee(),
                command.implementationPaid(),
                notes,
                command.expectedVersion(),
                principal.userId(),
                now
        ));
        if (updated == 0) {
            BranchBillingRecord current = repository.findByBranchId(branchId)
                    .orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "La sucursal no esta disponible."));
            if (current.version() != command.expectedVersion() || current.subscriptionStatus() != null) {
                throw new BusinessException(
                        "BILLING_VERSION_CONFLICT",
                        "La configuracion comercial cambio mientras la editabas. Recarga antes de guardar."
                );
            }
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

    @Transactional(readOnly = true)
    public List<BillingPaymentView> payments(GymOsPrincipal principal, UUID branchId) {
        requirePlatformAdmin(principal);
        requireBranch(branchId);
        return repository.findPayments(branchId).stream().map(this::toPaymentView).toList();
    }

    @Transactional
    public PaymentRegistration recordPayment(GymOsPrincipal principal, UUID branchId, RecordPaymentCommand command, String correlationId) {
        requirePlatformAdmin(principal);
        BranchBillingRecord branch = requireBranch(branchId);
        String paymentType = normalizePaymentType(command.paymentType());
        String paymentMethod = normalizePaymentMethod(command.paymentMethod());
        String idempotencyKey = requiredText(
                command.idempotencyKey(), 120, "BILLING_PAYMENT_IDEMPOTENCY_REQUIRED",
                "No fue posible identificar de forma segura este registro de cobro."
        );
        if (command.amount() == null || command.amount().signum() <= 0) {
            throw new BusinessException("BILLING_PAYMENT_AMOUNT_INVALID", "El monto del cobro debe ser mayor a cero.");
        }
        if (command.paidOn() == null) {
            throw new BusinessException("BILLING_PAYMENT_DATE_REQUIRED", "Captura la fecha en que recibiste el pago.");
        }
        if (command.paidOn().isAfter(LocalDate.now(BILLING_ZONE))) {
            throw new BusinessException("BILLING_PAYMENT_DATE_INVALID", "No puedes registrar un cobro con fecha futura.");
        }
        String reference = blankToNull(command.reference());
        String notes = blankToNull(command.notes());
        if (reference != null && reference.length() > 160) {
            throw new BusinessException("BILLING_PAYMENT_REFERENCE_TOO_LONG", "La referencia no puede exceder 160 caracteres.");
        }
        if (notes != null && notes.length() > 1000) {
            throw new BusinessException("BILLING_PAYMENT_NOTES_TOO_LONG", "Las notas no pueden exceder 1000 caracteres.");
        }

        BillingPaymentRecord existing = repository.findPaymentByIdempotencyKey(branchId, idempotencyKey).orElse(null);
        if (existing != null) {
            requireSamePayment(existing, paymentType, paymentMethod, command.amount(), command.paidOn(), reference, notes);
            return new PaymentRegistration(toView(branch), toPaymentView(existing));
        }

        BranchBillingView billing = toView(branch);
        if ("SUBSCRIPTION".equals(paymentType)
                && (!Set.of("ACTIVE", "PENDING").contains(billing.subscriptionStatus()) || branch.billingAnchorDay() == null)) {
            throw new BusinessException("BILLING_SUBSCRIPTION_NOT_READY", "Configura una suscripcion activa o pendiente antes de registrar su cobro.");
        }
        OffsetDateTime now = OffsetDateTime.now();
        BillingPaymentWrite payment = new BillingPaymentWrite(
                UUID.randomUUID(), branch.branchId(), branch.organizationId(), paymentType, paymentMethod,
                command.amount(), command.paidOn(), reference, notes, idempotencyKey, principal.userId(), now
        );
        if (repository.insertPayment(payment) == 0) {
            BillingPaymentRecord concurrent = repository.findPaymentByIdempotencyKey(branchId, idempotencyKey)
                    .orElseThrow(() -> new BusinessException(
                            "BILLING_PAYMENT_CONFLICT", "El cobro ya fue procesado. Recarga el historial."));
            requireSamePayment(
                    concurrent, paymentType, paymentMethod, command.amount(), command.paidOn(), reference, notes);
            return new PaymentRegistration(toView(requireBranch(branchId)), toPaymentView(concurrent));
        }

        if ("SUBSCRIPTION".equals(paymentType)) {
            repository.registerSubscriptionPayment(
                    branch.branchId(), command.paidOn(), command.amount(),
                    nextBillingDate(branch, command.paidOn()), principal.userId(), now
            );
        }
        if ("IMPLEMENTATION".equals(paymentType)
                && !branch.implementationPaid()
                && branch.implementationFee().signum() > 0
                && repository.paymentTotal(branch.branchId(), "IMPLEMENTATION").compareTo(branch.implementationFee()) >= 0) {
            repository.markImplementationPaid(branch.branchId(), principal.userId(), now);
        }

        BranchBillingRecord persisted = requireBranch(branchId);
        auditService.recordSystem(
                branch.organizationId(), branchId, principal.userId(),
                "PLATFORM_BILLING_PAYMENT_RECORDED", "PLATFORM_BILLING_PAYMENT", payment.id(), "PLATFORM_ADMIN",
                "Cobro comercial registrado: " + paymentType, correlationId
        );
        return new PaymentRegistration(toView(persisted), toPaymentView(new BillingPaymentRecord(
                payment.id(), payment.branchId(), payment.organizationId(), payment.paymentType(), payment.paymentMethod(),
                payment.amount(), payment.paidOn(), payment.reference(), payment.notes(), payment.idempotencyKey(),
                payment.recordedBy(), payment.createdAt()
        )));
    }

    private BillingSummary summary(List<BranchBillingView> branches) {
        Set<UUID> organizations = new LinkedHashSet<>();
        java.util.Map<UUID, Integer> teamUsersByOrganization = new java.util.LinkedHashMap<>();
        int activeMembers = 0;
        int overdue = 0;
        int unconfigured = 0;
        BigDecimal mrr = BigDecimal.ZERO;
        for (BranchBillingView branch : branches) {
            organizations.add(branch.organizationId());
            teamUsersByOrganization.merge(branch.organizationId(), branch.activeTeamUserCount(), Math::max);
            activeMembers += branch.activeMembers();
            if ("OVERDUE".equals(branch.paymentStatus())) overdue++;
            if ("UNCONFIGURED".equals(branch.subscriptionStatus())) unconfigured++;
            if ("ACTIVE".equals(branch.subscriptionStatus()) && branch.monthlyAmount() != null) {
                mrr = mrr.add(branch.monthlyAmount());
            }
        }
        int activeTeamUsers = teamUsersByOrganization.values().stream().mapToInt(Integer::intValue).sum();
        return new BillingSummary(organizations.size(), branches.size(), activeMembers, activeTeamUsers, mrr, overdue, unconfigured);
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
                record.activeTeamUserCount(), record.activeMembershipPlanCount(),
                suggested.code(), suggested.name(), suggested.monthlyPrice(),
                contracted.code(), contracted.name(), effectivePrice,
                subscriptionStatus, paymentStatus(subscriptionStatus, record.nextBillingDate()),
                record.billingAnchorDay(), record.nextBillingDate(), record.lastPaymentDate(), record.lastPaymentAmount(),
                record.monthlyPriceOverride(), record.implementationFee(), record.implementationPaid(),
                record.notes(), record.version()
        );
    }

    private BillingPaymentView toPaymentView(BillingPaymentRecord record) {
        return new BillingPaymentView(
                record.id(), record.branchId(), record.organizationId(), record.paymentType(), record.paymentMethod(),
                record.amount(), record.paidOn(), record.reference(), record.notes(), record.createdAt()
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

    private String normalizePaymentType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!PAYMENT_TYPES.contains(normalized)) {
            throw new BusinessException("BILLING_PAYMENT_TYPE_INVALID", "Selecciona un tipo de cobro valido.");
        }
        return normalized;
    }

    private String normalizePaymentMethod(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!PAYMENT_METHODS.contains(normalized)) {
            throw new BusinessException("BILLING_PAYMENT_METHOD_INVALID", "Selecciona un metodo de pago valido.");
        }
        return normalized;
    }

    private BranchBillingRecord requireBranch(UUID branchId) {
        return repository.findByBranchId(branchId)
                .orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "La sucursal no esta disponible."));
    }

    private LocalDate nextBillingDate(BranchBillingRecord branch, LocalDate paidOn) {
        LocalDate billedThrough = branch.nextBillingDate() == null || branch.nextBillingDate().isBefore(paidOn)
                ? paidOn
                : branch.nextBillingDate();
        int anchorDay = branch.billingAnchorDay();
        LocalDate candidate = billedThrough.withDayOfMonth(Math.min(anchorDay, billedThrough.lengthOfMonth()));
        if (!candidate.isAfter(billedThrough)) {
            candidate = candidate.plusMonths(1);
            candidate = candidate.withDayOfMonth(Math.min(anchorDay, candidate.lengthOfMonth()));
        }
        return candidate;
    }

    private void requireNonNegative(BigDecimal value, String code, String message) {
        if (value != null && value.signum() < 0) throw new BusinessException(code, message);
    }

    private void requireSamePayment(
            BillingPaymentRecord existing,
            String paymentType,
            String paymentMethod,
            BigDecimal amount,
            LocalDate paidOn,
            String reference,
            String notes
    ) {
        boolean same = existing.paymentType().equals(paymentType)
                && existing.paymentMethod().equals(paymentMethod)
                && existing.amount().compareTo(amount) == 0
                && existing.paidOn().equals(paidOn)
                && java.util.Objects.equals(existing.reference(), reference)
                && java.util.Objects.equals(existing.notes(), notes);
        if (!same) {
            throw new BusinessException(
                    "BILLING_PAYMENT_IDEMPOTENCY_CONFLICT",
                    "El identificador de este cobro ya fue usado con datos diferentes."
            );
        }
    }

    private String requiredText(String value, int maxLength, String code, String message) {
        String normalized = blankToNull(value);
        if (normalized == null || normalized.length() > maxLength) {
            throw new BusinessException(code, message);
        }
        return normalized;
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
            String contractedPlanCode,
            BigDecimal monthlyPriceOverride,
            BigDecimal implementationFee,
            boolean implementationPaid,
            String notes,
            long expectedVersion
    ) {}

    public record BillingOverview(BillingSummary summary, List<BranchBillingView> branches) {}

    public record BillingSummary(
            int customerCount,
            int branchCount,
            int activeMemberCount,
            int activeTeamUserCount,
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
            int activeTeamUserCount,
            int activeMembershipPlanCount,
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
            String notes,
            long version
    ) {}

    public record RecordPaymentCommand(
            String paymentType,
            String paymentMethod,
            BigDecimal amount,
            LocalDate paidOn,
            String reference,
            String notes,
            String idempotencyKey
    ) {}

    public record BillingPaymentView(
            UUID id,
            UUID branchId,
            UUID organizationId,
            String paymentType,
            String paymentMethod,
            BigDecimal amount,
            LocalDate paidOn,
            String reference,
            String notes,
            OffsetDateTime createdAt
    ) {}

    public record PaymentRegistration(BranchBillingView branch, BillingPaymentView payment) {}
}
