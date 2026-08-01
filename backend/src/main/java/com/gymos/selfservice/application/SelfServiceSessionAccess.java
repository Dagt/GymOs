package com.gymos.selfservice.application;

import com.gymos.common.api.BusinessException;
import com.gymos.selfservice.infrastructure.SelfServiceRepository;
import com.gymos.selfservice.infrastructure.SelfServiceRepository.SessionRecord;
import com.gymos.selfservice.infrastructure.SelfServiceSessionStatusRepository;
import com.gymos.selfservice.infrastructure.SelfServiceSessionStatusRepository.SessionStatusRecord;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SelfServiceSessionAccess {
    private static final Duration VERIFIED_SESSION_INACTIVITY = Duration.ofMinutes(3);

    private final SelfServiceRepository repository;
    private final SelfServiceSessionStatusRepository statusRepository;

    public SelfServiceSessionAccess(
            SelfServiceRepository repository,
            SelfServiceSessionStatusRepository statusRepository
    ) {
        this.repository = repository;
        this.statusRepository = statusRepository;
    }

    @Transactional
    public SessionRecord verified(UUID sessionId, String sessionToken) {
        SessionStatusRecord status = statusRepository.find(sessionId)
                .orElseThrow(() -> new BusinessException("SELF_SERVICE_SESSION_NOT_FOUND", "La sesion no existe."));
        validateToken(status.sessionTokenHash(), sessionToken);
        validateState(status, OffsetDateTime.now());
        if (!status.verified()) {
            throw new BusinessException("SELF_SERVICE_SESSION_NOT_VERIFIED", "Primero verifica tu identidad.");
        }
        return repository.session(sessionId).orElseThrow(() -> new BusinessException(
                "SELF_SERVICE_SESSION_UNAVAILABLE", "La sesion del kiosco no esta disponible."
        ));
    }

    private void validateToken(String storedHash, String token) {
        if (storedHash == null || token == null || !MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                tokenHash(token).getBytes(StandardCharsets.UTF_8)
        )) {
            throw new BusinessException("SELF_SERVICE_SESSION_INVALID", "La sesion del kiosco no es valida.");
        }
    }

    private void validateState(SessionStatusRecord session, OffsetDateTime now) {
        if (!"ACTIVE".equals(session.status())) {
            throw new BusinessException("SELF_SERVICE_SESSION_CLOSED", "La sesion del kiosco ya fue cerrada.");
        }
        if (session.expiresAt() == null || !session.expiresAt().isAfter(now)) {
            throw new BusinessException("SELF_SERVICE_SESSION_EXPIRED", "La sesion del kiosco expiro.");
        }
        if (session.verified() && session.updatedAt() != null
                && session.updatedAt().isBefore(now.minus(VERIFIED_SESSION_INACTIVITY))) {
            throw new BusinessException("SELF_SERVICE_SESSION_INACTIVE", "La sesion se cerro por inactividad.");
        }
    }

    private String tokenHash(String token) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
