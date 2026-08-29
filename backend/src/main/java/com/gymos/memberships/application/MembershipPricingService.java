package com.gymos.memberships.application;

import com.gymos.common.api.BusinessException;
import com.gymos.clients.api.operations.ClientLifecycleOperations;
import com.gymos.clients.api.operations.ClientLifecycleOperations.RegistrationFee;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.time.BusinessTime;
import com.gymos.eligibility.api.EligibilityPolicy;
import com.gymos.eligibility.api.EligibilityPolicy.EligibleBenefit;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityDecision;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityRequest;
import com.gymos.eligibility.api.EligibilityPolicy.Reason;
import com.gymos.memberships.application.result.MembershipQuoteResult;
import com.gymos.memberships.application.result.MembershipQuoteResult.AppliedBenefit;
import com.gymos.memberships.api.operations.MembershipPricingOperations;
import com.gymos.memberships.api.operations.MembershipPricingOperations.AppliedBenefitView;
import com.gymos.memberships.api.operations.MembershipPricingOperations.MembershipQuoteView;
import com.gymos.memberships.infrastructure.MembershipRepository;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipPricingService implements MembershipPricingOperations {
	private final MembershipRepository membershipRepository;
	private final EligibilityPolicy eligibilityPolicy;
	private final ClientLifecycleOperations clientLifecycleOperations;

	public MembershipPricingService(
			MembershipRepository membershipRepository,
			EligibilityPolicy eligibilityPolicy,
			ClientLifecycleOperations clientLifecycleOperations
	) {
		this.membershipRepository = membershipRepository;
		this.eligibilityPolicy = eligibilityPolicy;
		this.clientLifecycleOperations = clientLifecycleOperations;
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

	@Override
	@Transactional(readOnly = true)
	public MembershipQuoteView quoteForCheckout(GymOsPrincipal principal, UUID clientId, UUID planId) {
		MembershipQuoteResult quote = quote(principal, clientId, planId);
		return new MembershipQuoteView(
				quote.clientId(), quote.planId(), quote.planName(), quote.planPriceCents(),
				quote.registrationFeeCents(), quote.registrationFeeLabel(), quote.listPriceCents(),
				quote.discountCents(), quote.totalCents(), quote.currency(),
				quote.appliedBenefits().stream()
						.map(item -> new AppliedBenefitView(item.benefitId(), item.code(), item.name(), item.discountCents()))
						.toList()
		);
	}

	MembershipQuoteResult quote(
			UUID organizationId,
			UUID branchId,
			UUID clientId,
			PlanForRenewal plan
	) {
		EligibilityDecision eligibility = eligibilityPolicy.evaluate(EligibilityRequest.membershipPricing(
				organizationId, branchId, clientId, BusinessTime.today(), plan.planId()));
		if (!eligibility.eligible()) {
			throw eligibilityException(eligibility.reason());
		}
		List<EligibleBenefit> eligible = eligibility.benefits();
		QuoteCandidate combinable = applyCombinable(plan.priceCents(), eligible);
		QuoteCandidate exclusive = bestExclusive(plan.priceCents(), eligible);
		QuoteCandidate selected = exclusive.totalCents() < combinable.totalCents() ? exclusive : combinable;
		RegistrationFee registrationFee = "WAIVED".equals(plan.registrationFeePolicy())
				? RegistrationFee.none(plan.currency(), 0)
				: clientLifecycleOperations.registrationFee(organizationId, branchId, clientId, BusinessTime.today());
		if (registrationFee.due() && !plan.currency().equalsIgnoreCase(registrationFee.currency())) {
			throw new BusinessException(
					"REGISTRATION_FEE_CURRENCY_MISMATCH",
					"La moneda de la inscripción debe coincidir con la moneda del plan."
			);
		}
		long registrationFeeCents = registrationFee.due() ? registrationFee.amountCents() : 0;
		return new MembershipQuoteResult(
				clientId,
				plan.planId(),
				plan.planName(),
				plan.priceCents(),
				registrationFeeCents,
				registrationFee.due() ? registrationFee.label() : null,
				plan.priceCents() + registrationFeeCents,
				plan.priceCents() - selected.totalCents(),
				selected.totalCents() + registrationFeeCents,
				plan.currency(),
				selected.applied()
		);
	}

	private QuoteCandidate applyCombinable(long listPrice, List<EligibleBenefit> eligible) {
		long current = listPrice;
		List<AppliedBenefit> applied = new ArrayList<>();
		for (EligibleBenefit benefit : eligible.stream()
				.filter(EligibleBenefit::combinable)
				.sorted(Comparator.comparingInt(EligibleBenefit::priority))
				.toList()) {
			long next = apply(current, benefit);
			if (next < current) {
				applied.add(toApplied(benefit, current - next));
				current = next;
			}
		}
		return new QuoteCandidate(current, List.copyOf(applied));
	}

	private QuoteCandidate bestExclusive(long listPrice, List<EligibleBenefit> eligible) {
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

	private long apply(long current, EligibleBenefit benefit) {
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

	private AppliedBenefit toApplied(EligibleBenefit benefit, long discount) {
		return new AppliedBenefit(benefit.id(), benefit.code(), benefit.name(), discount);
	}

	private BusinessException eligibilityException(Reason reason) {
		return switch (reason) {
			case CLIENT_NOT_FOUND -> new BusinessException(
					"CLIENT_NOT_FOUND", "El cliente no existe o no esta disponible.");
			case CLIENT_NOT_ACTIVE -> new BusinessException(
					"CLIENT_NOT_ACTIVE", "El cliente debe estar activo para aplicar beneficios.");
			default -> new BusinessException(
					"MEMBERSHIP_PRICING_NOT_ELIGIBLE", "No fue posible determinar los beneficios aplicables.");
		};
	}

	private record QuoteCandidate(long totalCents, List<AppliedBenefit> applied) {
	}
}
