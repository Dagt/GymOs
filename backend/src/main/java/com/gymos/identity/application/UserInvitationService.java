package com.gymos.identity.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.AccountScope;
import com.gymos.common.security.StrongPasswordPolicy;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserInvitationService {
	private final IdentityProvisioningRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final StrongPasswordPolicy passwordPolicy;
	private final AuditService auditService;

	public UserInvitationService(
			IdentityProvisioningRepository repository,
			PasswordEncoder passwordEncoder,
			StrongPasswordPolicy passwordPolicy,
			AuditService auditService
	) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.auditService = auditService;
	}

	@Transactional
	public void accept(String token, String password, String correlationId) {
		passwordPolicy.validate(password);
		String tokenHash = sha256(requiredToken(token));
		var invitation = repository.lockInvitation(tokenHash).orElseThrow(this::invalidInvitation);
		OffsetDateTime now = OffsetDateTime.now();
		if (!"PENDING".equals(invitation.status())
				|| invitation.accountScope() != AccountScope.TENANT) {
			throw invalidInvitation();
		}
		if (!now.isBefore(invitation.expiresAt())) {
			repository.expireInvitation(invitation.invitationId());
			throw invalidInvitation();
		}
		if (repository.acceptInvitation(
				invitation.invitationId(), invitation.userId(), passwordEncoder.encode(password), now) != 1) {
			throw invalidInvitation();
		}
		auditService.recordSystem(
				invitation.organizationId(), null, invitation.userId(), "USER_INVITATION_ACCEPTED", "USER",
				invitation.userId(), "PUBLIC_INVITATION", null, correlationId);
	}

	public static String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String requiredToken(String token) {
		if (token == null || token.isBlank() || token.length() > 512) throw invalidInvitation();
		return token.trim();
	}

	private BusinessException invalidInvitation() {
		return new BusinessException(
				"USER_INVITATION_INVALID",
				"La invitacion no es valida o ya vencio. Solicita una nueva al administrador.");
	}
}
