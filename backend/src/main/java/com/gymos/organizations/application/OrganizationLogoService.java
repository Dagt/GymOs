package com.gymos.organizations.application;

import com.gymos.audit.api.AuditService;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.storage.ObjectStorageService;
import com.gymos.common.storage.ObjectStorageService.StoredObject;
import com.gymos.organizations.infrastructure.OrganizationCommandRepository;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository;
import com.gymos.organizations.infrastructure.OrganizationQueryRepository.OrganizationSummary;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrganizationLogoService {
	private static final long MAX_LOGO_SIZE = 2L * 1024 * 1024;
	private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final OrganizationCommandRepository commands;
	private final OrganizationQueryRepository organizations;
	private final ObjectStorageService storage;
	private final AuditService auditService;

	public OrganizationLogoService(
			OrganizationCommandRepository commands,
			OrganizationQueryRepository organizations,
			ObjectStorageService storage,
			AuditService auditService
	) {
		this.commands = commands;
		this.organizations = organizations;
		this.storage = storage;
		this.auditService = auditService;
	}

	@Transactional
	public OrganizationSummary upload(
			GymOsPrincipal principal,
			UploadOrganizationLogo command,
			String correlationId
	) {
		requirePrincipal(principal);
		return uploadForOrganization(principal, principal.organizationId(), command, correlationId);
	}

	@Transactional
	public OrganizationSummary uploadManaged(
			GymOsPrincipal principal,
			UUID organizationId,
			UploadOrganizationLogo command,
			String correlationId
	) {
		requirePrincipal(principal);
		if (!principal.platformAdmin()) {
			throw new BusinessException(
					"PERMISSION_DENIED",
					"Solo un administrador GymOS puede actualizar la marca de otro gimnasio."
			);
		}
		if (organizationId == null) {
			throw new BusinessException("ORGANIZATION_NOT_FOUND", "Selecciona un gimnasio valido.");
		}
		return uploadForOrganization(principal, organizationId, command, correlationId);
	}

	private OrganizationSummary uploadForOrganization(
			GymOsPrincipal principal,
			UUID organizationId,
			UploadOrganizationLogo command,
			String correlationId
	) {
		validate(command);

		String previousKey = organizations.findLogoObjectKey(organizationId).orElse(null);
		String newKey = objectKey(organizationId, command.filename());
		storage.putObject(newKey, command.contentType(), command.size(), command.content());
		registerStorageCompletion(newKey, previousKey);

		String logoUrl = "/api/tenant-logo/" + organizationId + "?v=" + UUID.randomUUID();
		int updated = commands.updateLogo(
				organizationId,
				principal.userId(),
				logoUrl,
				newKey,
				OffsetDateTime.now()
		);
		if (updated != 1) {
			throw new BusinessException("ORGANIZATION_NOT_FOUND", "El gimnasio de la sesion no esta disponible.");
		}

		auditService.record(
				principal,
				"ORGANIZATION_LOGO_UPDATED",
				"ORGANIZATION",
				organizationId,
				"BACKOFFICE",
				null,
				correlationId
		);
		return organizations.findCurrentOrganization(organizationId)
				.orElseThrow(() -> new BusinessException(
						"ORGANIZATION_NOT_FOUND",
						"El gimnasio de la sesion no esta disponible."
				));
	}

	@Transactional(readOnly = true)
	public Optional<StoredObject> read(UUID organizationId) {
		return organizations.findLogoObjectKey(organizationId).map(storage::getObject);
	}

	private void validate(UploadOrganizationLogo command) {
		if (command.content() == null || command.size() <= 0) {
			throw new BusinessException("ORGANIZATION_LOGO_INVALID", "Selecciona una imagen para el logo.");
		}
		if (command.size() > MAX_LOGO_SIZE) {
			throw new BusinessException("ORGANIZATION_LOGO_INVALID", "El logo no puede superar 2 MB.");
		}
		String contentType = command.contentType() == null
				? ""
				: command.contentType().toLowerCase(Locale.ROOT);
		if (!CONTENT_TYPES.contains(contentType)) {
			throw new BusinessException("ORGANIZATION_LOGO_INVALID", "Usa una imagen JPG, PNG o WebP.");
		}
	}

	private String objectKey(UUID organizationId, String filename) {
		String safeName = filename == null || filename.isBlank() ? "logo" : filename;
		safeName = safeName.replaceAll("[^A-Za-z0-9._-]", "_");
		return "branding/" + organizationId + "/" + UUID.randomUUID() + "-" + safeName;
	}

	private void registerStorageCompletion(String newKey, String previousKey) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			cleanup(newKey);
			throw new IllegalStateException("La actualizacion del logo requiere una transaccion activa.");
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status == STATUS_COMMITTED) {
					if (previousKey != null && !previousKey.equals(newKey)) cleanup(previousKey);
				} else {
					cleanup(newKey);
				}
			}
		});
	}

	private void cleanup(String objectKey) {
		try {
			storage.removeObject(objectKey);
		} catch (RuntimeException ignored) {
			// The persisted organization reference remains authoritative. Orphan
			// cleanup can be retried operationally without breaking the upload.
		}
	}

	private void requirePrincipal(GymOsPrincipal principal) {
		if (principal == null || principal.organizationId() == null) {
			throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
		}
	}

	public record UploadOrganizationLogo(
			String filename,
			String contentType,
			long size,
			InputStream content
	) {
	}
}
