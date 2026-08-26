package com.gymos.identity.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.StrongPasswordPolicy;
import com.gymos.identity.infrastructure.PasswordRecoveryRepository;
import com.gymos.notifications.api.operations.PasswordRecoveryEmailDelivery;
import com.gymos.notifications.api.operations.PasswordRecoveryEmailDelivery.RecoveryEmail;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PasswordRecoveryService {
	private final PasswordRecoveryRepository repository;
	private final UserAdministrationService users;
	private final PasswordEncoder encoder;
	private final StrongPasswordPolicy policy;
	private final PasswordRecoveryProperties properties;
	private final IdentityProperties identity;
	private final PasswordRecoveryEmailDelivery emailDelivery;
	private final AuditService audit;
	private final TransactionTemplate transactions;
	private final SecureRandom random = new SecureRandom();

	public PasswordRecoveryService(PasswordRecoveryRepository repository, UserAdministrationService users,
			PasswordEncoder encoder, StrongPasswordPolicy policy, PasswordRecoveryProperties properties,
			IdentityProperties identity, PasswordRecoveryEmailDelivery emailDelivery, AuditService audit, PlatformTransactionManager transactionManager) {
		this.repository = repository; this.users = users; this.encoder = encoder; this.policy = policy;
		this.properties = properties; this.identity = identity; this.emailDelivery = emailDelivery; this.audit = audit;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	/** Commit the hash before sending; plaintext exists only in this request and the email. */
	public RecoveryIssued issue(GymOsPrincipal principal, UUID userId, String correlationId) {
		RecoveryEmail email = transactions.execute(transaction -> {
			users.passwordRecoveryTarget(principal, userId);
			var user = repository.lockUser(principal.organizationId(), userId).orElseThrow(this::invalid);
			users.passwordRecoveryTarget(principal, userId);
			OffsetDateTime now = OffsetDateTime.now();
			if (repository.latestCreatedAt(userId).filter(created -> now.isBefore(created.plusSeconds(properties.resendCooldownSeconds()))).isPresent()) {
				throw new BusinessException("PASSWORD_RESET_COOLDOWN", "Ya se generó un enlace recientemente. Espera un momento antes de solicitar otro.");
			}
			byte[] bytes = new byte[32]; random.nextBytes(bytes);
			String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
			UUID id = UUID.randomUUID();
			OffsetDateTime expires = now.plusMinutes(properties.expiryMinutes());
			repository.revokePending(userId);
			repository.create(id, userId, UserInvitationService.sha256(token), expires, now, principal.userId());
			audit.record(principal, "STAFF_PASSWORD_RECOVERY_ISSUED", "USER", userId, "ADMIN", null, correlationId);
			return new RecoveryEmail(id, user.organizationId(), user.name(), user.email(),
					identity.frontendBaseUrl() + "/reset-password#token=" + token, expires);
		});
		if (email == null) throw new IllegalStateException("Password recovery transaction returned no result");
		String deliveryStatus;
		try { deliveryStatus = emailDelivery.deliver(email); }
		catch (RuntimeException ignored) { deliveryStatus = "FAILED"; } // Never log transport exceptions containing the link.
		repository.recordDelivery(email.id(), deliveryStatus);
		return new RecoveryIssued(email.url(), email.expiresAt(), deliveryStatus);
	}

	public void consume(String token, String password, String correlationId) {
		policy.validate(password);
		if (token == null || token.isBlank() || token.length() > 512) throw invalid();
		String hash = UserInvitationService.sha256(token.trim());
		transactions.executeWithoutResult(transaction -> {
			UUID userId = repository.userIdForToken(hash).orElseThrow(this::invalid);
			var user = repository.lockUser(null, userId).orElseThrow(this::invalid);
			var recovery = repository.lockToken(hash).orElseThrow(this::invalid);
			OffsetDateTime now = OffsetDateTime.now();
			if (!"PENDING".equals(recovery.status()) || !now.isBefore(recovery.expiresAt())) throw invalid();
			repository.consume(recovery, encoder.encode(password), now);
			audit.recordSystem(user.organizationId(), null, userId, "STAFF_PASSWORD_RECOVERED", "USER", userId,
					"PASSWORD_RECOVERY", null, correlationId);
		});
	}

	private BusinessException invalid() {
		return new BusinessException("PASSWORD_RESET_INVALID", "El enlace no es válido, ya se utilizó o venció. Solicita uno nuevo al administrador del gimnasio.");
	}

	public record RecoveryIssued(String resetUrl, OffsetDateTime expiresAt, String emailDeliveryStatus) {
		@Override public String toString() { return "RecoveryIssued[resetUrl=REDACTED, expiresAt=" + expiresAt + ", emailDeliveryStatus=" + emailDeliveryStatus + "]"; }
	}
}
