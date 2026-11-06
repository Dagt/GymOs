package com.gymos.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private final JwtService jwtService;
	private final UserCredentialVersionRepository credentials;
	private final SessionRegistry sessions;

	public JwtAuthenticationFilter(JwtService jwtService, UserCredentialVersionRepository credentials, SessionRegistry sessions) {
		this.jwtService = jwtService;
		this.credentials = credentials;
		this.sessions = sessions;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization != null && authorization.startsWith("Bearer ")) {
			try {
				jwtService.parseAccessToken(authorization.substring(7)).ifPresent(this::authenticate);
			} catch (DataAccessException unavailable) {
				response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
				response.setHeader("Cache-Control", "no-store");
				response.setContentType("application/json");
				response.getWriter().write("""
						{"code":"SESSION_UNAVAILABLE","message":"No se pudo verificar la sesión. Intenta de nuevo."}
						""");
				return;
			}
		}
		filterChain.doFilter(request, response);
	}

	private void authenticate(GymOsPrincipal principal) {
		SessionBinding binding = principal.session();
		if (binding == null || binding.authorityFingerprint() == null || !sessions.isCurrent(binding)) return;
		if (!credentials.isCurrent(principal)
				|| !binding.authorityFingerprint().equals(credentials.authorityFingerprint(principal.userId()))) {
			sessions.revokeContext(binding.id(), binding.revision());
			return;
		}
		var authorities = principal.permissions().stream().map(SimpleGrantedAuthority::new).toList();
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
	}
}
