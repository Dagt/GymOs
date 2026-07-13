package com.gymos.security.application;

import com.gymos.security.infrastructure.AuthQueryRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class GymOsUserDetailsService implements UserDetailsService {
	private final AuthQueryRepository authQueryRepository;

	public GymOsUserDetailsService(AuthQueryRepository authQueryRepository) {
		this.authQueryRepository = authQueryRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return authQueryRepository.findActiveUserByUsername(username)
				.map(user -> User.withUsername(user.username())
						.password(user.passwordHash())
						.authorities(user.permissions().toArray(String[]::new))
						.accountLocked(false)
						.disabled(false)
						.build())
				.orElseThrow(() -> new UsernameNotFoundException(username));
	}
}
