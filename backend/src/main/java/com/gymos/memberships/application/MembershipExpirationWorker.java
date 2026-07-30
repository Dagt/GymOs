package com.gymos.memberships.application;

import com.gymos.common.time.BusinessTime;
import com.gymos.memberships.infrastructure.MembershipRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class MembershipExpirationWorker {
	private final MembershipRepository membershipRepository;

	MembershipExpirationWorker(MembershipRepository membershipRepository) {
		this.membershipRepository = membershipRepository;
	}

	@Scheduled(fixedDelayString = "${gymos.memberships.expiration-delay-ms:60000}")
	@Transactional
	public void expireDueMemberships() {
		membershipRepository.expireAllDueMemberships(BusinessTime.today());
	}
}
