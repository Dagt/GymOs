package com.gymos.automations.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.automations.infrastructure.ExpiringMembershipRepository;
import com.gymos.automations.infrastructure.ExpiringMembershipRepository.ExpiringMembership;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MembershipExpiryTriggerScannerTests {
	@Test
	void createsStableOccurrenceWithRenderedVariables() throws Exception {
		AutomationRepository rules = mock(AutomationRepository.class);
		ExpiringMembershipRepository memberships = mock(ExpiringMembershipRepository.class);
		AutomationService service = mock(AutomationService.class);
		ObjectMapper mapper = new ObjectMapper();
		MembershipExpiryTriggerScanner scanner = new MembershipExpiryTriggerScanner(
				rules, memberships, service, mapper
		);

		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID ruleId = UUID.randomUUID();
		UUID membershipId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID planId = UUID.randomUUID();
		LocalDate today = LocalDate.of(2026, 8, 1);
		LocalDate endsOn = today.plusDays(5);
		AutomationRuleRecord rule = new AutomationRuleRecord(
				organizationId, branchId, ruleId, "Aviso", "MEMBERSHIP_EXPIRING",
				"{\"daysBeforeExpiry\":5}", "CREATE_NOTIFICATION",
				"{\"body\":\"Vence {{endsOn}}\"}", "ENABLED", 3,
				null, null, null, 7L, OffsetDateTime.now(), OffsetDateTime.now()
		);
		when(rules.enabledRulesByTrigger("MEMBERSHIP_EXPIRING", 200)).thenReturn(List.of(rule));
		when(memberships.find(organizationId, branchId, endsOn, 1000))
				.thenReturn(List.of(new ExpiringMembership(membershipId, clientId, planId, endsOn)));

		int created = scanner.materialize(today);

		assertThat(created).isEqualTo(1);
		ArgumentCaptor<String> config = ArgumentCaptor.forClass(String.class);
		verify(service).enqueueDomainOccurrence(
				eq(rule),
				eq("membership-expiring:" + ruleId + ":" + membershipId + ":" + endsOn),
				eq("membership:" + membershipId),
				any(OffsetDateTime.class),
				config.capture()
		);
		var json = mapper.readTree(config.getValue());
		assertThat(json.path("variables").path("membershipId").asText()).isEqualTo(membershipId.toString());
		assertThat(json.path("variables").path("endsOn").asText()).isEqualTo(endsOn.toString());
		assertThat(json.path("variables").path("daysBeforeExpiry").asInt()).isEqualTo(5);
	}
}
