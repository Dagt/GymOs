package com.gymos.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
	private final JwtService jwtService;
	private final UserCredentialVersionRepository credentials;

	public JwtAuthenticationFilter(JwtService jwtService, UserCredentialVersionRepository credentials) {
		this.jwtService = jwtService;
		this.credentials = credentials;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String authorization = request.getHeader("Authorization");
		if (authorization != null && authorization.startsWith("Bearer ")) {
			jwtService.parseAccessToken(authorization.substring(7)).ifPresent(principal -> {
				if (!credentials.isCurrent(principal)) return;
				var authorities = principal.permissions().stream()
						.map(SimpleGrantedAuthority::new)
						.toList();
				var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
				SecurityContextHolder.getContext().setAuthentication(authentication);
			});
		}
		filterChain.doFilter(request, response);
	}
}
