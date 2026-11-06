package com.gymos.security.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gymos.audit.api.AuditService;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.JwtService;
import com.gymos.common.security.RefreshCredentials;
import com.gymos.common.security.SecurityProperties;
import com.gymos.common.security.SessionBinding;
import com.gymos.common.security.SessionContext;
import com.gymos.common.security.SessionRegistry;
import com.gymos.common.security.UserAuthorities;
import com.gymos.security.infrastructure.AuthQueryRepository;
import com.gymos.security.infrastructure.AuthQueryRepository.AuthenticatedUser;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;

final class AuthSessionTestFixture {
	private static final SecurityProperties PROPERTIES = new SecurityProperties(null, null, 900, 7);
	final AuthenticationManager authentication = mock(AuthenticationManager.class);
	final AuthQueryRepository users = mock(AuthQueryRepository.class);
	final SessionRegistry sessions = mock(SessionRegistry.class);
	final AuditService audit = mock(AuditService.class);
	final RefreshCredentials credentials = new RefreshCredentials(PROPERTIES);
	final AuthService service = new AuthService(authentication, users, new JwtService(PROPERTIES), sessions,
			credentials, audit, PROPERTIES);

	ActiveSession session(AuthenticatedUser user, GymOsPrincipal principal) {
		UUID id = UUID.randomUUID();
		String fingerprint = new UserAuthorities(user.permissions(), user.roles()).fingerprint();
		String context = SessionContext.from(principal, fingerprint, Instant.now().plusSeconds(3600)).serialize();
		when(sessions.find(id)).thenReturn(Optional.of(new SessionRegistry.State(0, 0, context)));
		return new ActiveSession(principal.withSession(new SessionBinding(id, 0, fingerprint)),
				id, credentials.issue(id, 0), context);
	}

	record ActiveSession(GymOsPrincipal principal, UUID id, String cookie, String context) {
	}
}
