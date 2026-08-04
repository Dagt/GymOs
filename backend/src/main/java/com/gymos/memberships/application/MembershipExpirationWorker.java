package com.gymos.memberships.application;

import com.gymos.common.time.BusinessTime;
import com.gymos.memberships.api.events.MembershipExpiredV1;
import com.gymos.memberships.api.events.MembershipExpiringV1;
import com.gymos.memberships.infrastructure.MembershipRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class MembershipExpirationWorker {
	private final MembershipRepository membershipRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final int expiringHorizonDays;

	MembershipExpirationWorker(
			MembershipRepository membershipRepository,
			ApplicationEventPublisher eventPublisher,
			@Value("${gymos.memberships.expiring-horizon-days:90}") int expiringHorizonDays
	) {
		this.membershipRepository = membershipRepository;
		this.eventPublisher = eventPublisher;
		this.expiringHorizonDays = Math.max(1, Math.min(expiringHorizonDays, 90));
	}

	@Scheduled(fixedDelayString = "${gymos.memberships.expiration-delay-ms:60000}")
	@Transactional
	public void expireDueMemberships() {
		LocalDate businessDate = BusinessTime.today();
		publishExpiringMemberships(businessDate);
		membershipRepository.expireAllDueMemberships(businessDate);
		publishExpiredMemberships(businessDate);
	}

	private void publishExpiringMemberships(LocalDate businessDate) {
		for (var membership : membershipRepository.unpublishedExpiringMemberships(
				businessDate, expiringHorizonDays, 1000
		)) {
			if (!membershipRepository.recordExpiringOccurrence(membership, businessDate)) {
				continue;
			}
			int daysUntilEnd = Math.toIntExact(ChronoUnit.DAYS.between(businessDate, membership.endsOn()));
			eventPublisher.publishEvent(new MembershipExpiringV1(
					"membership-expiring:" + membership.membershipId() + ":" + businessDate,
					membership.organizationId(), membership.branchId(), membership.clientId(),
					membership.membershipId(), membership.planId(), membership.endsOn(),
					membership.graceEndsOn(), businessDate, daysUntilEnd, OffsetDateTime.now()
			));
		}
	}

	private void publishExpiredMemberships(LocalDate businessDate) {
		for (var membership : membershipRepository.claimUnpublishedExpiredMemberships(1000)) {
			eventPublisher.publishEvent(new MembershipExpiredV1(
					"membership-expired:" + membership.membershipId() + ":" + membership.graceEndsOn(),
					membership.organizationId(), membership.branchId(), membership.clientId(),
					membership.membershipId(), membership.planId(), membership.endsOn(),
					membership.graceEndsOn(), businessDate, OffsetDateTime.now()
			));
		}
	}
}
