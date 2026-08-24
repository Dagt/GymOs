package com.gymos.platformadmin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.AccountScope;
import com.gymos.organizations.api.operations.PlatformOrganizationOperations;
import com.gymos.identity.api.operations.TenantOwnerProvisioning;
import com.gymos.identity.api.operations.TenantOwnerProvisioning.OwnerInvitation;
import com.gymos.organizations.api.operations.PlatformOrganizationOperations.ManagedOrganization;
import com.gymos.platformadmin.application.PlatformCustomerService;
import com.gymos.platformadmin.application.PlatformCustomerService.UpdateCustomerAccountCommand;
import com.gymos.platformadmin.application.PlatformCustomerService.CreateCustomerCommand;
import com.gymos.platformadmin.infrastructure.PlatformCustomerAccountRepository;
import com.gymos.platformadmin.infrastructure.PlatformCustomerAccountRepository.CustomerAccountRecord;
import com.gymos.platformbilling.api.operations.PlatformBillingOperations;
import com.gymos.platformbilling.api.operations.PlatformBillingOperations.BranchCommercialStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformCustomerServiceTests {
    private final PlatformOrganizationOperations organizationOperations = mock(PlatformOrganizationOperations.class);
    private final PlatformCustomerAccountRepository accountRepository = mock(PlatformCustomerAccountRepository.class);
    private final PlatformBillingOperations billingOperations = mock(PlatformBillingOperations.class);
    private final AuditService auditService = mock(AuditService.class);
    private final TenantOwnerProvisioning ownerProvisioning = mock(TenantOwnerProvisioning.class);
    private final PlatformCustomerService service = new PlatformCustomerService(
            organizationOperations, accountRepository, billingOperations, auditService, ownerProvisioning);

    @Test
    void buildsACommercialPortfolioAndPrioritizesOverdueCustomers() {
        GymOsPrincipal admin = platformAdmin();
        UUID organizationId = UUID.randomUUID();
        ManagedOrganization organization = organization(organizationId, "Gym Norte", "ACTIVE", 2, 2);
        CustomerAccountRecord account = account(organizationId, "ACTIVE", 3);
        when(organizationOperations.organizations()).thenReturn(List.of(organization));
        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(billingOperations.branches(admin)).thenReturn(List.of(branch(organizationId, "OVERDUE")));

        var portfolio = service.portfolio(admin);

        assertThat(portfolio.summary().customerCount()).isEqualTo(1);
        assertThat(portfolio.summary().atRiskCustomerCount()).isEqualTo(1);
        assertThat(portfolio.summary().monthlyRecurringRevenue()).isEqualByComparingTo("1590.00");
        assertThat(portfolio.customers().getFirst().healthStatus()).isEqualTo("AT_RISK");
        assertThat(portfolio.customers().getFirst().accountOwner()).isEqualTo("Daniela");
        assertThat(portfolio.customers().getFirst().version()).isEqualTo(3);
    }

    @Test
    void rejectsAStaleCommercialDossierWithoutAuditingIt() {
        GymOsPrincipal admin = platformAdmin();
        UUID organizationId = UUID.randomUUID();
        when(organizationOperations.organization(organizationId))
                .thenReturn(Optional.of(organization(organizationId, "Gym Norte", "ACTIVE", 1, 1)));
        when(accountRepository.upsert(any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(
                admin,
                organizationId,
                new UpdateCustomerAccountCommand(
                        "ACTIVE", "Daniela", "cobranza@gym.mx", "+52 55 1234 5678",
                        LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 15,
                        LocalDate.of(2026, 8, 28), "Seguimiento mensual", 2),
                "correlation"
        ));

        assertThat(exception.code()).isEqualTo("CUSTOMER_ACCOUNT_VERSION_CONFLICT");
        verify(auditService, never()).recordSystem(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void tenantUsersCannotReadThePlatformCustomerPortfolio() {
        GymOsPrincipal tenant = new GymOsPrincipal(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "owner", "Owner", Set.of());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.portfolio(tenant));

        assertThat(exception.code()).isEqualTo("PLATFORM_ACCESS_REQUIRED");
    }

	@Test
	void provisionsOrganizationBranchAndInvitedOwnerAsOneUseCase() {
		PlatformCustomerService provisioningService = new PlatformCustomerService(
				organizationOperations, accountRepository, billingOperations, auditService, ownerProvisioning);
		GymOsPrincipal admin = platformAdmin();
		UUID ownerUserId = UUID.randomUUID();
		when(ownerProvisioning.provisionOwner(any())).thenReturn(new OwnerInvitation(
				ownerUserId, "https://gymfort.mx/accept-invitation?token=secret",
				OffsetDateTime.now().plusHours(72)));
		when(organizationOperations.organization(any())).thenAnswer(invocation -> Optional.of(
				organization(invocation.getArgument(0), "Gym Nuevo", "ACTIVE", 1, 1)));

		var result = provisioningService.create(admin, new CreateCustomerCommand(
				"Gym Nuevo SA de CV", "Gym Nuevo", "GNUE260101AA1", "America/Mexico_City", "MXN",
				"contacto@gymnuevo.mx", "+525512345678", "Gym Nuevo", null,
				"#093D3A", "#18C8B2", "#F2F8F7", "Sucursal Principal", "MAIN", "+525512345678",
				"Ana", "Propietaria", "ana@gymnuevo.mx", "+525587654321"), "correlation");

		assertThat(result.customer().branchCount()).isEqualTo(1);
		assertThat(result.ownerEmail()).isEqualTo("ana@gymnuevo.mx");
		assertThat(result.invitationUrl()).contains("/accept-invitation?token=");
		verify(organizationOperations).create(any());
		verify(ownerProvisioning).provisionOwner(any());
	}

    private ManagedOrganization organization(
            UUID id, String tradeName, String status, int branchCount, int activeBranchCount) {
        return new ManagedOrganization(
                id, tradeName + " SA de CV", tradeName, "GNO260101AA1", status,
                "direccion@gym.mx", "+52 55 1000 1000", tradeName, null,
                branchCount, activeBranchCount);
    }

    private CustomerAccountRecord account(UUID organizationId, String lifecycle, long version) {
        return new CustomerAccountRecord(
                organizationId, lifecycle, "Daniela", "cobranza@gym.mx", "+52 55 1234 5678",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1), 15,
                LocalDate.now().plusDays(3), "Seguimiento mensual", version,
                OffsetDateTime.parse("2026-08-20T12:00:00-06:00"));
    }

    private BranchCommercialStatus branch(UUID organizationId, String paymentStatus) {
        return new BranchCommercialStatus(
                organizationId, 420, new BigDecimal("1590.00"), "ACTIVE", paymentStatus,
                new BigDecimal("3500.00"), true, LocalDate.now().minusDays(2));
    }

    private GymOsPrincipal platformAdmin() {
		return new GymOsPrincipal(
				UUID.randomUUID(), AccountScope.PLATFORM, null, null, "platform", "Platform Admin",
				Set.of("platform.admin"), Set.of("Administrador GymOS"), false, null);
    }
}
