package com.gymos.identity.api.operations;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.AccountScope;
import com.gymos.common.security.StrongPasswordPolicy;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository;
import com.gymos.identity.infrastructure.IdentityProvisioningRepository.PlatformAdministratorState;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class PlatformAdministratorBootstrapAdapter implements PlatformAdministratorBootstrap {
	private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final IdentityProvisioningRepository repository;
	private final PasswordEncoder passwordEncoder;
	private final StrongPasswordPolicy passwordPolicy;
	private final AuditService auditService;

	PlatformAdministratorBootstrapAdapter(
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

	@Override
	@Transactional
	public boolean bootstrap(BootstrapCommand command) {
		if (!command.enabled()) return false;
		repository.acquirePlatformAdministratorBootstrapLock();
		PlatformAdministratorState administratorState = repository.platformAdministratorState();
		if (administratorState == PlatformAdministratorState.ACTIVE) return false;
		if (administratorState == PlatformAdministratorState.INACTIVE) {
			throw new BusinessException(
					"PLATFORM_BOOTSTRAP_ADMIN_INACTIVE",
					"Existe una cuenta administradora de plataforma que requiere recuperacion operativa.");
		}

		String email = required(command.email(), "PLATFORM_BOOTSTRAP_EMAIL_REQUIRED", "Falta el correo del administrador de plataforma.")
				.toLowerCase(Locale.ROOT);
		String name = required(command.name(), "PLATFORM_BOOTSTRAP_NAME_REQUIRED", "Falta el nombre del administrador de plataforma.");
		if (!EMAIL.matcher(email).matches() || email.length() > 180) {
			throw new BusinessException("PLATFORM_BOOTSTRAP_EMAIL_INVALID", "El correo del administrador de plataforma no es valido.");
		}
		passwordPolicy.validate(command.password());
		if (repository.accountScopeForUsername(email).isPresent()) {
			throw new BusinessException(
					"PLATFORM_BOOTSTRAP_ACCOUNT_CONFLICT",
					"Ya existe una cuenta incompatible con el correo configurado para bootstrap.");
		}

		String[] names = splitName(name);
		OffsetDateTime now = OffsetDateTime.now();
		var personId = repository.createPerson(
				AccountScope.PLATFORM, null, names[0], names[1], email, null, null, now);
		var userId = repository.createUser(
				AccountScope.PLATFORM, null, personId, email,
				passwordEncoder.encode(command.password()), "ACTIVE", null, now);
		repository.assignRole(userId, repository.platformAdministratorRoleId());
		auditService.recordSystem(
				null, null, userId, "PLATFORM_ADMIN_BOOTSTRAPPED", "USER", userId,
				"SYSTEM_BOOTSTRAP", null, "platform-bootstrap");
		return true;
	}

	private String required(String value, String code, String message) {
		if (value == null || value.isBlank()) throw new BusinessException(code, message);
		return value.trim();
	}

	private String[] splitName(String fullName) {
		int separator = fullName.lastIndexOf(' ');
		if (separator <= 0 || separator == fullName.length() - 1) {
			return new String[] { fullName, "Plataforma" };
		}
		return new String[] { fullName.substring(0, separator).trim(), fullName.substring(separator + 1).trim() };
	}
}
