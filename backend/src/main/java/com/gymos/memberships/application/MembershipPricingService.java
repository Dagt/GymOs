package com.gymos.memberships.application;

import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import com.gymos.memberships.application.result.MembershipQuoteResult;
import com.gymos.memberships.application.result.MembershipQuoteResult.AppliedBenefit;
import com.gymos.memberships.infrastructure.MembershipPricingRepository;
import com.gymos.memberships.infrastructure.MembershipPricingRepository.EligiblePricingBenefit;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipPricingService {
	private final MembershipRepository membershipRepository;
	private final MembershipPricingRepository pricingRepository;

	public MembershipPricingService(
			MembershipRepository membershipRepository,
			MembershipPricingRepository pricingRepository
	) {
		this.membershipRepository = membershipRepository;
		this.pricingRepository = pricingRepository;
	}

	@Transactional(readOnly = true)
	public MembershipQuoteResult quote(GymOsPrincipal principal, UUID clientId, UUID planId) {
		MembershipAccessGuard.requireBranch(principal);
		if (!membershipRepository.clientExists(principal.organizationId(), clientId)) {
			throw new BusinessException("CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible.");
		}
		PlanForRenewal plan = membershipRepository.planForRenewal(principal.organizationId(), planId)
				.orElseThrow(() -> new BusinessException("PLAN_NOT_AVAILABLE", "El plan no esta disponible."));
		return quote(principal.organizationId(), principal.branchId(), clientId, plan);
	}

	MembershipQuoteResult quote(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			PlanForRenewal plan
	) {
		List<EligiblePricingBenefit> eligible = pricingRepository.eligibleBenefits(
				organizationId, branchId, clientId, BusinessTime.today());
		QuoteCandidate combinable = applyCombinable(plan.priceCents(), eligible);
		QuoteCandidate exclusive = bestExclusive(plan.priceCents(), eligible);
		QuoteCandidate selected = exclusive.totalCents() < combinable.totalCents() ? exclusive : combinable;
		return new MembershipQuoteResult(
				clientId,
				plan.planId(),
				plan.planName(),
				plan.priceCents(),
				plan.priceCents() - selected.totalCents(),
				selected.totalCents(),
				plan.currency(),
				selected.applied()
		);
	}

	private QuoteCandidate applyCombinable(long listPrice, List<EligiblePricingBenefit> eligible) {
		long current = listPrice;
		List<AppliedBenefit> applied = new ArrayList<>();
		for (EligiblePricingBenefit benefit : eligible.stream()
				.filter(EligiblePricingBenefit::combinable)
				.sorted(Comparator.comparingInt(EligiblePricingBenefit::priority))
				.toList()) {
			long next = apply(current, benefit);
			if (next < current) {
				applied.add(toApplied(benefit, current - next));
				current = next;
			}
		}
		return new QuoteCandidate(current, List.copyOf(applied));
	}

	private QuoteCandidate bestExclusive(long listPrice, List<EligiblePricingBenefit> eligible) {
		return eligible.stream()
				.filter(benefit -> !benefit.combinable())
				.map(benefit -> {
					long total = apply(listPrice, benefit);
					return new QuoteCandidate(total, total < listPrice
							? List.of(toApplied(benefit, listPrice - total))
							: List.of());
				})
				.min(Comparator.comparingLong(QuoteCandidate::totalCents))
				.orElse(new QuoteCandidate(listPrice, List.of()));
	}

	private long apply(long current, EligiblePricingBenefit benefit) {
		return switch (benefit.discountType()) {
			case "PERCENT" -> {
				double percent = benefit.discountPercent() == null ? 0 : benefit.discountPercent();
				yield Math.max(0, current - Math.round(current * percent / 100.0));
			}
			case "FIXED" -> Math.max(0, current - Math.max(0, benefit.discountValueCents() == null
					? 0 : benefit.discountValueCents()));
			case "SPECIAL_PRICE" -> Math.min(current, Math.max(0, benefit.discountValueCents() == null
					? current : benefit.discountValueCents()));
			default -> current;
		};
	}

	private AppliedBenefit toApplied(EligiblePricingBenefit benefit, long discount) {
		return new AppliedBenefit(benefit.id(), benefit.code(), benefit.name(), discount);
	}

	private record QuoteCandidate(long totalCents, List<AppliedBenefit> applied) {
	}
}
