
package com.gymos.access.application;

import com.gymos.access.infrastructure.ManualCheckInRepository;
import com.gymos.access.infrastructure.ManualCheckInRepository.BranchContext;
import com.gymos.access.infrastructure.ManualCheckInRepository.Client;
import com.gymos.access.infrastructure.ManualCheckInRepository.Entry;
import com.gymos.access.infrastructure.ManualCheckInRepository.Request;
import com.gymos.audit.api.AuditService;
import com.gymos.catalog.api.VisitPackageBenefitUsageMode;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.eligibility.api.EligibilityPolicy;
import com.gymos.eligibility.api.EligibilityPolicy.ActiveMembership;
import com.gymos.eligibility.api.EligibilityPolicy.EligibilityRequest;
import com.gymos.eligibility.api.EligibilityPolicy.Reason;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations;
import com.gymos.visitpackages.api.operations.VisitPackageAccessOperations.VisitPackageAccessResult;
import com.gymos.visitpackages.api.operations.VisitPackageOperations.VisitPackageEntitlementView;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualCheckInService {
	public static final String OPERATE = "access.check-in";
	public static final String OVERRIDE = "access.check-in.override";
	public static final String REVERSE = "access.check-in.reverse";
	private static final int DUPLICATE_SECONDS = 60;
	private static final String CONSUMES_VISIT = VisitPackageBenefitUsageMode.CONSUMES_VISIT.name();

	private final ManualCheckInRepository repository;
	private final EligibilityPolicy eligibility;
	private final VisitPackageAccessOperations packages;
	private final AuditService audit;

	public ManualCheckInService(ManualCheckInRepository repository, EligibilityPolicy eligibility,
			VisitPackageAccessOperations packages, AuditService audit) {
		this.repository = repository;
		this.eligibility = eligibility;
		this.packages = packages;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<Client> search(GymOsPrincipal actor, String query) {
		BranchContext branch = branch(actor);
		String term = query.strip();
		if (term.length() < 2 || term.length() > 100) {
			throw new BusinessException("CHECK_IN_SEARCH_INVALID", "Escribe entre 2 y 100 caracteres para buscar.");
		}
		return repository.search(actor, localDate(branch, now()), term);
	}

	@Transactional(readOnly = true)
	public Preview preview(GymOsPrincipal actor, UUID clientId) {
		BranchContext branch = branch(actor);
		LocalDate date = localDate(branch, now());
		Client client = client(actor, date, clientId);
		var decision = eligibility.evaluate(EligibilityRequest.access(
				actor.organizationId(), actor.branchId(), clientId, date, Set.of()));
		PackageView visitPackage = packageView(actor, clientId, date);
		var options = new LinkedHashMap<UUID, AccessOption>();
		decision.benefits().forEach(benefit -> options.putIfAbsent(benefit.id(),
				new AccessOption(benefit.id(), benefit.name(), "MEMBERSHIP")));
		if (decision.reason() != Reason.CLIENT_NOT_ACTIVE && decision.reason() != Reason.CLIENT_NOT_FOUND
				&& usablePackage(visitPackage)) {
			visitPackage.benefits().stream().filter(benefit -> CONSUMES_VISIT.equals(benefit.mode()))
					.forEach(benefit -> options.putIfAbsent(benefit.id(),
							new AccessOption(benefit.id(), benefit.name(), "VISIT_PACKAGE")));
		}
		return new Preview(client, decision.membership(), visitPackage, List.copyOf(options.values()),
				options.isEmpty() ? denial(decision.reason(), visitPackage) : null);
	}

	@Transactional(readOnly = true)
	public History history(GymOsPrincipal actor) {
		BranchContext branch = branch(actor);
		return new History(actor.branchId(), branch.name(), branch.zone().getId(), repository.history(actor));
	}

	@Transactional
	public Entry register(GymOsPrincipal actor, UUID branchId, UUID key, UUID clientId, UUID benefitId,
			UUID overrideOf, String overrideReason, String correlationId) {
		BranchContext branch = branch(actor);
		checkBranch(actor, branchId);
		String reason = overrideOf == null ? null : requiredReason(overrideReason);
		if (overrideOf != null) {
			require(actor, OVERRIDE);
		} else if (overrideReason != null) {
			throw new BusinessException("CHECK_IN_OVERRIDE_INVALID", "Selecciona la entrada previa para justificar otra entrada.");
		}
		repository.lockRequest(actor, key);
		Request previous = repository.request(actor, key).orElse(null);
		if (previous != null) {
			if (!previous.branchId().equals(branchId) || !previous.clientId().equals(clientId)
					|| !Objects.equals(previous.benefitId(), benefitId)
					|| !Objects.equals(previous.overrideOf(), overrideOf)
					|| !Objects.equals(previous.overrideReason(), reason)) {
				throw new BusinessException("CHECK_IN_KEY_REUSED", "La clave ya pertenece a otra operación.");
			}
			return entry(actor, previous.id());
		}

		repository.lockClient(actor, clientId);
		OffsetDateTime now = now();
		LocalDate date = localDate(branch, now);
		client(actor, date, clientId);
		UUID recent = repository.recentGrant(actor, clientId, now.minusSeconds(DUPLICATE_SECONDS)).orElse(null);
		if (overrideOf != null && !overrideOf.equals(recent)) {
			throw new BusinessException("CHECK_IN_OVERRIDE_STALE", "La entrada previa cambió o salió de la ventana. Vuelve a consultar al socio.");
		}
		if (recent != null && overrideOf == null) {
			return record(actor, key, clientId, benefitId, now, date,
					new Outcome("DUPLICATE", "Ya se registró una entrada para este socio en el último minuto.", null, null),
					recent, null, null, correlationId);
		}

		Outcome outcome = decide(actor, key, clientId, benefitId, date, now);
		return record(actor, key, clientId, benefitId, now, date, outcome, null, overrideOf, reason, correlationId);
	}

	@Transactional
	public Entry reverse(GymOsPrincipal actor, UUID branchId, UUID id, String reason, String correlationId) {
		branch(actor);
		checkBranch(actor, branchId);
		require(actor, REVERSE);
		String explanation = requiredReason(reason);
		Entry original = entry(actor, id);
		repository.lockClient(actor, original.clientId());
		original = entry(actor, id);
		if (original.reversal() != null) {
			if (original.reversal().actorUserId().equals(actor.userId())
					&& original.reversal().reason().equals(explanation)) {
				return original;
			}
			throw new BusinessException("CHECK_IN_ALREADY_REVERSED", "Esta entrada ya fue revertida.");
		}
		if (!"GRANTED".equals(original.decision())) {
			throw new BusinessException("CHECK_IN_REVERSAL_NOT_ALLOWED", "Sólo se puede revertir una entrada autorizada.");
		}

		OffsetDateTime now = now();
		var restoration = original.entitlementId() == null ? null : packages.reverseConsumption(
				actor.organizationId(), actor.branchId(), original.clientId(), id, explanation, now);
		repository.reverse(actor, id, now, explanation, restoration);
		audit.record(actor, "MANUAL_CHECK_IN_REVERSED", "ACCESS_LOG", id, "MANUAL", explanation, correlationId);
		return entry(actor, id);
	}

	private Outcome decide(GymOsPrincipal actor, UUID key, UUID clientId, UUID benefitId,
			LocalDate date, OffsetDateTime now) {
		var membership = eligibility.evaluate(EligibilityRequest.access(
				actor.organizationId(), actor.branchId(), clientId, date,
				benefitId == null ? Set.of() : Set.of(benefitId)));
		if (membership.eligible() && benefitId != null) {
			UUID membershipId = membership.benefits().stream().filter(benefit -> benefit.id().equals(benefitId))
					.findFirst().orElseThrow().membershipId();
			return new Outcome("GRANTED", "Entrada autorizada por membresía.", membershipId, null);
		}
		if (membership.reason() == Reason.CLIENT_NOT_ACTIVE || membership.reason() == Reason.CLIENT_NOT_FOUND) {
			return new Outcome("DENIED", denial(membership.reason(), null), null, null);
		}

		PackageView visitPackage = packageView(actor, clientId, date);
		if (benefitId != null && usablePackage(visitPackage)
				&& visitPackage.benefits().stream().anyMatch(benefit ->
						benefit.id().equals(benefitId) && CONSUMES_VISIT.equals(benefit.mode()))) {
			var usage = packages.authorizeForAccess(actor.organizationId(), actor.branchId(), clientId,
					List.of(benefitId), null, "manual-check-in:" + key, now);
			if (usage.isPresent() && usage.get().granted() && usage.get().consumedVisit()) {
				return new Outcome("GRANTED", "Entrada autorizada con una visita del paquete.", null, usage.get());
			}
			throw new IllegalStateException("The visit package changed during the locked manual check-in");
		}
		return new Outcome("DENIED", denial(membership.reason(), visitPackage), null, null);
	}

	private Entry record(GymOsPrincipal actor, UUID key, UUID clientId, UUID benefitId, OffsetDateTime now,
			LocalDate date, Outcome outcome, UUID duplicateOf, UUID overrideOf, String overrideReason, String correlationId) {
		UUID id = repository.insert(actor, key, clientId, benefitId, outcome.membershipId(), now, date,
				outcome.decision(), outcome.reason(), duplicateOf, overrideOf, overrideReason, logCorrelation(correlationId));
		if (outcome.usage() != null) {
			packages.linkAccessLog(actor.organizationId(), outcome.usage().usageId(), outcome.usage().usageMode(), id);
		}
		audit.record(actor, "MANUAL_CHECK_IN_" + outcome.decision(), "ACCESS_LOG", id, "MANUAL",
				overrideReason == null ? outcome.reason() : outcome.reason() + " Motivo: " + overrideReason, correlationId);
		return entry(actor, id);
	}

	private PackageView packageView(GymOsPrincipal actor, UUID clientId, LocalDate date) {
		return packages.packageForAccess(actor.organizationId(), clientId)
				.map(pkg -> packageView(pkg, actor.branchId(), date)).orElse(null);
	}

	private PackageView packageView(VisitPackageEntitlementView pkg, UUID branchId, LocalDate date) {
		String status = pkg.status();
		if (("ACTIVE".equals(status) || "CONSUMED".equals(status))
				&& pkg.validUntil() != null && pkg.validUntil().isBefore(date)) {
			status = "EXPIRED";
		} else if ("ACTIVE".equals(status) && pkg.validFrom() != null && pkg.validFrom().isAfter(date)) {
			status = "NOT_STARTED";
		}
		return new PackageView(pkg.id(), pkg.packageName(), status, pkg.remainingVisits(), pkg.validFrom(),
				pkg.validUntil(), pkg.branchIds().contains(branchId), pkg.benefits().stream()
				.filter(benefit -> benefit.branchIds().contains(branchId))
				.map(benefit -> new PackageBenefit(benefit.benefitId(), benefit.name(), benefit.usageMode())).toList());
	}

	private boolean usablePackage(PackageView pkg) {
		return pkg != null && "ACTIVE".equals(pkg.status()) && pkg.branchCovered()
				&& pkg.remainingVisits() > 0 && pkg.validFrom() != null && pkg.validUntil() != null
				&& !pkg.validFrom().isAfter(pkg.validUntil());
	}

	private String denial(Reason membershipReason, PackageView pkg) {
		if (membershipReason == Reason.CLIENT_NOT_ACTIVE || membershipReason == Reason.CLIENT_NOT_FOUND) {
			return "El socio está inactivo o suspendido.";
		}
		if (pkg != null) {
			if (!pkg.branchCovered()) return "El paquete no cubre esta sucursal y la membresía no autoriza este acceso.";
			if ("EXPIRED".equals(pkg.status())) return "El paquete está vencido y la membresía no autoriza este acceso.";
			if ("CONSUMED".equals(pkg.status())) return "El paquete está agotado y la membresía no autoriza este acceso.";
		}
		return membershipReason == Reason.ACTIVE_MEMBERSHIP_REQUIRED
				? "Sin membresía vigente en esta sucursal ni paquete que autorice este acceso."
				: "Los beneficios vigentes no autorizan la entrada solicitada.";
	}

	private BranchContext branch(GymOsPrincipal actor) {
		require(actor, OPERATE);
		return repository.branch(actor).orElseThrow(() -> new AccessDeniedException("Sucursal no disponible."));
	}

	private void require(GymOsPrincipal actor, String permission) {
		if (actor == null || !actor.tenantIdentity() || actor.organizationId() == null || actor.branchId() == null
				|| !actor.permissions().contains(permission)) {
			throw new AccessDeniedException("No tienes permiso para operar entradas en esta sucursal.");
		}
	}

	private void checkBranch(GymOsPrincipal actor, UUID branchId) {
		if (!actor.branchId().equals(branchId)) {
			throw new AccessDeniedException("La sucursal seleccionada cambió.");
		}
	}

	private Client client(GymOsPrincipal actor, LocalDate date, UUID clientId) {
		return repository.client(actor, date, clientId).orElseThrow(() ->
				new BusinessException("CHECK_IN_CLIENT_NOT_FOUND", "El socio no está disponible en esta sucursal."));
	}

	private Entry entry(GymOsPrincipal actor, UUID id) {
		return repository.entry(actor, id).orElseThrow(() ->
				new BusinessException("CHECK_IN_NOT_FOUND", "La entrada no está disponible en esta sucursal."));
	}

	private String requiredReason(String reason) {
		if (reason == null || reason.isBlank() || reason.strip().length() > 300) {
			throw new BusinessException("CHECK_IN_REASON_REQUIRED", "Indica un motivo de hasta 300 caracteres.");
		}
		return reason.strip();
	}

	private static LocalDate localDate(BranchContext branch, OffsetDateTime now) {
		return now.atZoneSameInstant(branch.zone()).toLocalDate();
	}

	private static OffsetDateTime now() {
		return OffsetDateTime.now(ZoneOffset.UTC);
	}

	private static UUID logCorrelation(String value) {
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException | NullPointerException ignored) {
			return UUID.nameUUIDFromBytes(("gymfort:" + value).getBytes(StandardCharsets.UTF_8));
		}
	}

	private record Outcome(String decision, String reason, UUID membershipId, VisitPackageAccessResult usage) {}
	public record AccessOption(UUID id, String name, String source) {}
	public record PackageBenefit(UUID id, String name, String mode) {}
	public record PackageView(UUID id, String name, String status, int remainingVisits, LocalDate validFrom,
			LocalDate validUntil, boolean branchCovered, List<PackageBenefit> benefits) {}
	public record Preview(Client client, ActiveMembership membership, PackageView visitPackage,
			List<AccessOption> options, String denialReason) {}
	public record History(UUID branchId, String branchName, String timezone, List<Entry> entries) {}
}
