package com.gymos.automations.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gymos.automations.infrastructure.AutomationRepository;
import com.gymos.automations.infrastructure.AutomationRepository.AutomationRuleRecord;
import com.gymos.automations.infrastructure.ExpiredMembershipRepository;
import com.gymos.automations.infrastructure.ExpiredMembershipRepository.ExpiredMembership;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class MembershipExpiredTriggerScanner {
    private final AutomationRepository automationRepository;
    private final ExpiredMembershipRepository membershipRepository;
    private final AutomationService automationService;
    private final ObjectMapper objectMapper;

    public MembershipExpiredTriggerScanner(
            AutomationRepository automationRepository,
            ExpiredMembershipRepository membershipRepository,
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
        LocalDate expiredOn = today.minusDays(1);
        for (AutomationRuleRecord rule : automationRepository.enabledRulesByTrigger("MEMBERSHIP_EXPIRED", 200)) {
            for (ExpiredMembership membership : membershipRepository.find(
                    rule.organizationId(), rule.branchId(), expiredOn, 1000
            )) {
                automationService.enqueueDomainOccurrence(
                        rule,
                        "membership-expired:" + rule.id() + ":" + membership.id() + ":" + membership.endsOn(),
                        "membership:" + membership.id(),
                        today.atStartOfDay().atOffset(ZoneOffset.UTC),
                        actionConfig(rule, membership)
                );
                created++;
            }
        }
        return created;
    }

    private String actionConfig(AutomationRuleRecord rule, ExpiredMembership membership) {
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
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid automation action configuration", exception);
        }
    }
}
