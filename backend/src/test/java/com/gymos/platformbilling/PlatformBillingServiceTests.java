package com.gymos.platformbilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.platformbilling.application.PlatformBillingService;
import com.gymos.platformbilling.application.PlatformBillingService.UpdateBillingCommand;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository.BillingPaymentRecord;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository.BranchBillingRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformBillingServiceTests {
    private final PlatformBillingRepository repository = mock(PlatformBillingRepository.class);
    private final AuditService auditService = mock(AuditService.class);
    private final PlatformBillingService service = new PlatformBillingService(repository, auditService);

    @Test
    void resolvesSuggestedPlansAndConfiguredMrrPerBranch() {
        UUID organizationId = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of(
                row(organizationId, "Centro", 800, "ACTIVE", "GYMFORT_1000", null),
                row(organizationId, "Norte", 350, "ACTIVE", "GYMFORT_500", null),
                row(organizationId, "Nueva", 120, null, null, null)
        ));

        var overview = service.overview(platformAdmin());

        assertThat(overview.summary().customerCount()).isEqualTo(1);
        assertThat(overview.summary().branchCount()).isEqualTo(3);
        assertThat(overview.summary().activeMemberCount()).isEqualTo(1270);
        assertThat(overview.summary().activeTeamUserCount()).isEqualTo(4);
        assertThat(overview.summary().monthlyRecurringRevenue()).isEqualByComparingTo("3780.00");
        assertThat(overview.summary().unconfiguredBranchCount()).isEqualTo(1);

        assertThat(overview.branches().get(0).suggestedPlanName()).isEqualTo("GymFort 1000");
        assertThat(overview.branches().get(0).suggestedMonthlyPrice()).isEqualByComparingTo("2190.00");
        assertThat(overview.branches().get(0).activeMembershipPlanCount()).isEqualTo(3);
        assertThat(overview.branches().get(1).suggestedPlanName()).isEqualTo("GymFort 500");
        assertThat(overview.branches().get(2).suggestedPlanName()).isEqualTo("GymFort 150");
        assertThat(overview.branches().get(2).subscriptionStatus()).isEqualTo("UNCONFIGURED");
    }

    @Test
    void enterpriseHasNoAutomaticListPrice() {
        UUID organizationId = UUID.randomUUID();
        when(repository.findAll()).thenReturn(List.of(row(organizationId, "Mega", 2300, null, null, null)));

        var branch = service.overview(platformAdmin()).branches().getFirst();

        assertThat(branch.suggestedPlanName()).isEqualTo("GymFort Enterprise");
        assertThat(branch.suggestedMonthlyPrice()).isNull();
        assertThat(branch.monthlyAmount()).isNull();
    }

    @Test
    void rejectsManualPriceOnStandardPlans() {
        UpdateBillingCommand command = new UpdateBillingCommand(
                "ACTIVE",
                15,
                LocalDate.now().plusMonths(1),
                "GYMFORT_500",
                new BigDecimal("999.00"),
                new BigDecimal("3500.00"),
                false,
                null,
                0
        );

        assertThrows(BusinessException.class, () -> service.update(
                platformAdmin(), UUID.randomUUID(), command, "test-correlation"
        ));
    }

    @Test
    void requiresAQuotedMonthlyPriceForEnterprise() {
        UpdateBillingCommand command = new UpdateBillingCommand(
                "ACTIVE",
                15,
                LocalDate.now().plusMonths(1),
                "ENTERPRISE",
                null,
                BigDecimal.ZERO,
                false,
                null,
                0
        );

        assertThrows(BusinessException.class, () -> service.update(
                platformAdmin(), UUID.randomUUID(), command, "test-correlation"
        ));
    }

    @Test
    void rejectsTenantUsers() {
        GymOsPrincipal tenant = new GymOsPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "owner", "Owner", Set.of("organization.read")
        );
        assertThrows(BusinessException.class, () -> service.overview(tenant));
    }

    @Test
    void recordsSubscriptionPaymentAndMovesNextCutoffToTheFollowingAnchor() {
        UUID organizationId = UUID.randomUUID();
        BranchBillingRecord branch = withNextBillingDate(
                row(organizationId, "Centro", 180, "ACTIVE", "GYMFORT_500", null),
                LocalDate.of(2026, 8, 15)
        );
        UUID branchId = branch.branchId();
        when(repository.findByBranchId(branchId)).thenReturn(java.util.Optional.of(branch));
        when(repository.insertPayment(any())).thenReturn(1);

        var result = service.recordPayment(
                platformAdmin(), branchId,
                new PlatformBillingService.RecordPaymentCommand(
                        "SUBSCRIPTION", "TRANSFER", new BigDecimal("1590.00"),
                        LocalDate.of(2026, 8, 15), "SPEI-123", "Agosto", "payment-august"
                ),
                "test-correlation"
        );

        assertThat(result.payment().paymentType()).isEqualTo("SUBSCRIPTION");
        assertThat(result.payment().amount()).isEqualByComparingTo("1590.00");
        verify(repository).registerSubscriptionPayment(
                org.mockito.ArgumentMatchers.eq(branchId),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 8, 15)),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("1590.00")),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 9, 15)),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void advancesTheCurrentInvoiceWhenSubscriptionIsReceivedBeforeItsCutoff() {
        BranchBillingRecord branch = withNextBillingDate(
                row(UUID.randomUUID(), "Centro", 180, "ACTIVE", "GYMFORT_500", null),
                LocalDate.of(2026, 8, 15)
        );
        when(repository.findByBranchId(branch.branchId())).thenReturn(java.util.Optional.of(branch));
        when(repository.insertPayment(any())).thenReturn(1);

        service.recordPayment(
                platformAdmin(), branch.branchId(),
                new PlatformBillingService.RecordPaymentCommand(
                        "SUBSCRIPTION", "TRANSFER", new BigDecimal("1590.00"),
                        LocalDate.of(2026, 8, 10), "SPEI-early", null, "payment-early"
                ),
                "test-correlation"
        );

        verify(repository).registerSubscriptionPayment(
                org.mockito.ArgumentMatchers.eq(branch.branchId()),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 8, 10)),
                org.mockito.ArgumentMatchers.eq(new BigDecimal("1590.00")),
                org.mockito.ArgumentMatchers.eq(LocalDate.of(2026, 9, 15)),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void marksImplementationAsPaidWhenCollectedAmountCoversTheFee() {
        BranchBillingRecord branch = withImplementationFee(
                row(UUID.randomUUID(), "Centro", 180, "ACTIVE", "GYMFORT_500", null),
                new BigDecimal("3500.00")
        );
        when(repository.findByBranchId(branch.branchId())).thenReturn(java.util.Optional.of(branch));
        when(repository.paymentTotal(branch.branchId(), "IMPLEMENTATION")).thenReturn(new BigDecimal("3500.00"));
        when(repository.insertPayment(any())).thenReturn(1);

        service.recordPayment(
                platformAdmin(), branch.branchId(),
                new PlatformBillingService.RecordPaymentCommand(
                        "IMPLEMENTATION", "TRANSFER", new BigDecimal("3500.00"),
                        LocalDate.of(2026, 8, 15), "SPEI-124", null, "implementation-payment"
                ),
                "test-correlation"
        );

        verify(repository).markImplementationPaid(
                org.mockito.ArgumentMatchers.eq(branch.branchId()),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void doesNotPersistSubscriptionPaymentWithoutAnActiveOrPendingSubscription() {
        BranchBillingRecord branch = row(UUID.randomUUID(), "Centro", 180, "PAUSED", "GYMFORT_500", null);
        when(repository.findByBranchId(branch.branchId())).thenReturn(java.util.Optional.of(branch));

        assertThrows(BusinessException.class, () -> service.recordPayment(
                platformAdmin(), branch.branchId(),
                new PlatformBillingService.RecordPaymentCommand(
                        "SUBSCRIPTION", "TRANSFER", new BigDecimal("1590.00"),
                        LocalDate.of(2026, 8, 15), null, null, "paused-payment"
                ),
                "test-correlation"
        ));

        verify(repository, never()).insertPayment(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsTheOriginalPaymentWhenTheSameIdempotencyKeyIsRetried() {
        BranchBillingRecord branch = withNextBillingDate(
                row(UUID.randomUUID(), "Centro", 180, "ACTIVE", "GYMFORT_500", null),
                LocalDate.of(2026, 8, 15)
        );
        BillingPaymentRecord existing = payment(branch, "payment-retry", new BigDecimal("1590.00"));
        when(repository.findByBranchId(branch.branchId())).thenReturn(java.util.Optional.of(branch));
        when(repository.findPaymentByIdempotencyKey(branch.branchId(), "payment-retry"))
                .thenReturn(java.util.Optional.of(existing));

        var result = service.recordPayment(
                platformAdmin(), branch.branchId(),
                new PlatformBillingService.RecordPaymentCommand(
                        "SUBSCRIPTION", "TRANSFER", new BigDecimal("1590.00"),
                        LocalDate.of(2026, 8, 15), "SPEI-RETRY", "Agosto", "payment-retry"
                ),
                "test-correlation"
        );

        assertThat(result.payment().id()).isEqualTo(existing.id());
        verify(repository, never()).insertPayment(any());
        verify(repository, never()).registerSubscriptionPayment(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsReusingAnIdempotencyKeyWithDifferentPaymentData() {
        BranchBillingRecord branch = row(UUID.randomUUID(), "Centro", 180, "ACTIVE", "GYMFORT_500", null);
        BillingPaymentRecord existing = payment(branch, "payment-conflict", new BigDecimal("1590.00"));
        when(repository.findByBranchId(branch.branchId())).thenReturn(java.util.Optional.of(branch));
        when(repository.findPaymentByIdempotencyKey(branch.branchId(), "payment-conflict"))
                .thenReturn(java.util.Optional.of(existing));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.recordPayment(
                platformAdmin(), branch.branchId(),
                new PlatformBillingService.RecordPaymentCommand(
                        "SUBSCRIPTION", "TRANSFER", new BigDecimal("1.00"),
                        LocalDate.of(2026, 8, 15), "SPEI-RETRY", "Agosto", "payment-conflict"
                ),
                "test-correlation"
        ));

        assertThat(exception.code()).isEqualTo("BILLING_PAYMENT_IDEMPOTENCY_CONFLICT");
        verify(repository, never()).insertPayment(any());
    }

    @Test
    void rejectsAnUpdateWhenTheCommercialVersionChanged() {
        BranchBillingRecord branch = row(UUID.randomUUID(), "Centro", 180, "ACTIVE", "GYMFORT_500", null);
        when(repository.upsert(any())).thenReturn(0);
        when(repository.findByBranchId(branch.branchId())).thenReturn(java.util.Optional.of(branch));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(
                platformAdmin(), branch.branchId(),
                new UpdateBillingCommand(
                        "ACTIVE", 15, LocalDate.now().plusMonths(1), "GYMFORT_500", null,
                        BigDecimal.ZERO, false, null, branch.version() - 1
                ),
                "test-correlation"
        ));

        assertThat(exception.code()).isEqualTo("BILLING_VERSION_CONFLICT");
    }

    private BranchBillingRecord row(
            UUID organizationId,
            String branchName,
            int activeMembers,
            String subscriptionStatus,
            String planCode,
            BigDecimal monthlyOverride
    ) {
        return new BranchBillingRecord(
                organizationId,
                "Gym de prueba",
                "ACTIVE",
                UUID.randomUUID(),
                branchName,
                branchName.toUpperCase(),
                "ACTIVE",
                4,
                3,
                activeMembers,
                subscriptionStatus,
                subscriptionStatus == null ? null : 15,
                subscriptionStatus == null ? null : LocalDate.now().plusDays(10),
                null,
                null,
                planCode,
                monthlyOverride,
                BigDecimal.ZERO,
                false,
                null,
                1
        );
    }

    private BranchBillingRecord withImplementationFee(BranchBillingRecord branch, BigDecimal implementationFee) {
        return new BranchBillingRecord(
                branch.organizationId(), branch.organizationName(), branch.organizationStatus(),
                branch.branchId(), branch.branchName(), branch.branchCode(), branch.branchStatus(),
                branch.activeTeamUserCount(), branch.activeMembershipPlanCount(), branch.activeMembers(),
                branch.subscriptionStatus(), branch.billingAnchorDay(), branch.nextBillingDate(),
                branch.lastPaymentDate(), branch.lastPaymentAmount(), branch.planOverride(), branch.monthlyPriceOverride(),
                implementationFee, false, branch.notes(), branch.version()
        );
    }

    private BranchBillingRecord withNextBillingDate(BranchBillingRecord branch, LocalDate nextBillingDate) {
        return new BranchBillingRecord(
                branch.organizationId(), branch.organizationName(), branch.organizationStatus(),
                branch.branchId(), branch.branchName(), branch.branchCode(), branch.branchStatus(),
                branch.activeTeamUserCount(), branch.activeMembershipPlanCount(), branch.activeMembers(),
                branch.subscriptionStatus(), branch.billingAnchorDay(), nextBillingDate,
                branch.lastPaymentDate(), branch.lastPaymentAmount(), branch.planOverride(), branch.monthlyPriceOverride(),
                branch.implementationFee(), branch.implementationPaid(), branch.notes(), branch.version()
        );
    }

    private BillingPaymentRecord payment(BranchBillingRecord branch, String key, BigDecimal amount) {
        return new BillingPaymentRecord(
                UUID.randomUUID(), branch.branchId(), branch.organizationId(), "SUBSCRIPTION", "TRANSFER",
                amount, LocalDate.of(2026, 8, 15), "SPEI-RETRY", "Agosto", key,
                UUID.randomUUID(), OffsetDateTime.parse("2026-08-15T12:00:00-06:00")
        );
    }

    private GymOsPrincipal platformAdmin() {
        return new GymOsPrincipal(
                UUID.randomUUID(), null, null, "platform", "Platform Admin", Set.of("platform.admin")
        );
    }
}
