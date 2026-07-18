package com.gymos.common.security;

import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(
								"/actuator/health/**",
								"/api/v1/system/**",
								"/api/v1/auth/login",
								"/api/v1/auth/refresh",
								"/api/v1/self-service/**",
								"/api/v1/openapi/**",
								"/api/v1/docs/**",
								"/swagger-ui/**"
						).permitAll()
						.requestMatchers("/api/v1/auth/select-branch").hasAuthority("branch.read")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/users/roles").hasAuthority("user.read")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/users/internal").hasAuthority("user.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/users/internal").hasAuthority("user.manage")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/select-organization").hasAuthority("organization.read")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/organizations").hasAuthority("organization.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/organizations").hasAuthority("organization.update")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/organizations/current/branches").hasAuthority("branch.create")
						.requestMatchers("/api/v1/organizations/current/branches/*").hasAuthority("branch.update")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/organizations/current/branches").hasAuthority("branch.read")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/organizations/*/branches").hasAuthority("platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/organizations/current").hasAuthority("organization.read")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/organizations/current").hasAuthority("organization.update")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/onboarding/**").hasAuthority("onboarding.manage")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/onboarding/**").hasAuthority("onboarding.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/clients/*/workouts").hasAuthority("workout.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/clients/imports").hasAuthority("client.import")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/clients/*/benefits").hasAuthority("client.benefit.assign")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/clients/**").hasAuthority("client.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/clients").hasAuthority("client.create")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/clients/**").hasAuthority("client.update")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/catalog/**").hasAuthority("catalog.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/catalog/**").hasAuthority("catalog.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/benefits/**").hasAuthority("benefit.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/benefits/**").hasAuthority("benefit.manage")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/client-benefits/*/verify").hasAuthority("client.benefit.verify")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/memberships/**").hasAuthority("membership.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/memberships/*/renew").hasAuthority("membership.renew")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/memberships/*/change-plan").hasAuthority("membership.change-plan")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/sales/reports/**").hasAuthority("report.sales.read")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/sales/**").hasAuthority("sale.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sales").hasAuthority("sale.create")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sales/*/refunds").hasAuthority("sale.refund")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sales/*/payments").hasAuthority("payment.manual.create")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sales/payments/*/validate").hasAuthority("payment.manual.validate")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sales/payments/*/reject").hasAuthority("payment.manual.reject")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sales/payments/*/receipt").hasAuthority("payment.receipt.upload")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/access/visit-passes/**").hasAuthority("visit.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/access/visit-passes/*/consume").hasAuthority("visit.consume")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/access/visit-passes").hasAuthority("visit.create")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/access/**").hasAuthority("access.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/access/**").hasAuthority("access.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/inventory/**").hasAuthority("inventory.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/inventory/**").hasAuthority("inventory.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/resources/**").hasAuthority("resource.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/resources/**").hasAuthority("resource.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/bookings/**").hasAuthority("booking.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/bookings/*/payments").hasAuthority("booking.payment.manage")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/bookings/**").hasAuthority("booking.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/coaches/**").hasAuthority("coach.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/coaches/**").hasAuthority("coach.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/workouts/**").hasAuthority("workout.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/workouts/**").hasAuthority("workout.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/reports/coaches").hasAuthority("report.coaches.read")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/professionals/**").hasAuthority("professional.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/professionals/**").hasAuthority("professional.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/notifications/**").hasAuthority("notification.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/notifications/**").hasAuthority("notification.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/automations/**").hasAuthority("automation.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/automations/**").hasAuthority("automation.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/reports/owner-dashboard").hasAuthority("report.owner.read")
						.anyRequest().authenticated())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(new StandardAuthenticationEntryPoint()))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.build();
	}

	@Bean
	FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(JwtAuthenticationFilter filter) {
		FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
		registration.setEnabled(false);
		return registration;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
		return corsConfigurationSource(properties.corsAllowedOrigins());
	}

	private CorsConfigurationSource corsConfigurationSource(List<String> allowedOrigins) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(allowedOrigins);
		configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setExposedHeaders(List.of("X-Correlation-Id"));
		configuration.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
