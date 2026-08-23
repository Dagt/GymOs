package com.gymos.platformbilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.platformbilling.application.PlatformBillingService;
import com.gymos.platformbilling.application.PlatformBillingService.UpdateBillingCommand;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository;
import com.gymos.platformbilling.infrastructure.PlatformBillingRepository.BranchBillingRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
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
        assertThat(overview.summary().monthlyRecurringRevenue()).isEqualByComparingTo("3780.00");
        assertThat(overview.summary().unconfiguredBranchCount()).isEqualTo(1);

        assertThat(overview.branches().get(0).suggestedPlanName()).isEqualTo("GymFort 1000");
        assertThat(overview.branches().get(0).suggestedMonthlyPrice()).isEqualByComparingTo("2190.00");
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
                null,
                null,
                "GYMFORT_500",
                new BigDecimal("999.00"),
                new BigDecimal("3500.00"),
                false,
                null
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
                null,
                null,
                "ENTERPRISE",
                null,
                BigDecimal.ZERO,
                false,
                null
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
                null
        );
    }

    private GymOsPrincipal platformAdmin() {
        return new GymOsPrincipal(
                UUID.randomUUID(), null, null, "platform", "Platform Admin", Set.of("platform.admin")
        );
    }
}
