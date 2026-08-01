package com.gymos.automations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.automations.infrastructure.ExpiringMembershipRepository;
import com.gymos.automations.infrastructure.ExpiringMembershipRepository.ExpiringMembership;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class MembershipExpiryTriggerScanner {
	private final AutomationRepository automationRepository;
	private final ExpiringMembershipRepository membershipRepository;
	private final AutomationService automationService;
	private final ObjectMapper objectMapper;

	public MembershipExpiryTriggerScanner(
			AutomationRepository automationRepository,
			ExpiringMembershipRepository membershipRepository,
			AutomationService automationService,
			ObjectMapper objectMapper
	) {
		this.automationRepository = automationRepository;
		this.membershipRepository = membershipRepository;
		this.automationService = automationService;
		this.objectMapper = objectMapper;
	}

	public int materialize(LocalDate today) {
		int created = 0;
		for (AutomationRuleRecord rule : automationRepository.enabledRulesByTrigger("MEMBERSHIP_EXPIRING", 200)) {
			int days = daysBeforeExpiry(rule.conditionJson());
			for (ExpiringMembership membership : membershipRepository.find(
					rule.organizationId(), rule.branchId(), today.plusDays(days), 1000
			)) {
				automationService.enqueueDomainOccurrence(
						rule,
						"membership-expiring:" + rule.id() + ":" + membership.id() + ":" + membership.endsOn(),
						"membership:" + membership.id(),
						today.atStartOfDay().atOffset(ZoneOffset.UTC),
						actionConfig(rule, membership, days)
				);
				created++;
			}
		}
		return created;
	}

	private String actionConfig(AutomationRuleRecord rule, ExpiringMembership membership, int days) {
		try {
			JsonNode parsed = objectMapper.readTree(rule.actionConfigJson());
			ObjectNode config = parsed != null && parsed.isObject()
					? (ObjectNode) parsed.deepCopy()
					: objectMapper.createObjectNode();
			ObjectNode variables = config.withObject("variables");
			variables.put("membershipId", membership.id().toString());
			variables.put("clientId", membership.clientId().toString());
			variables.put("planId", membership.planId().toString());
			variables.put("endsOn", membership.endsOn().toString());
			variables.put("daysBeforeExpiry", days);
			return objectMapper.writeValueAsString(config);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Invalid automation action configuration", exception);
		}
	}

	private int daysBeforeExpiry(String conditionJson) {
		try {
			return Math.max(1, Math.min(objectMapper.readTree(conditionJson)
					.path("daysBeforeExpiry").asInt(7), 90));
		} catch (JsonProcessingException exception) {
			return 7;
		}
	}
}
