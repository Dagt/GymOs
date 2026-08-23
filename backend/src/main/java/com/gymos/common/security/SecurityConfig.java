package com.gymos.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
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
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			JwtAuthenticationFilter jwtAuthenticationFilter,
			ObjectMapper objectMapper
	) throws Exception {
		return http.csrf(AbstractHttpConfigurer::disable).cors(Customizer.withDefaults())
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/api/v1/payment-webhooks/**").permitAll()
						.requestMatchers("/api/v1/physical-connectors/**").permitAll()
						.requestMatchers(org.springframework.http.HttpMethod.GET,
								"/api/v1/payment-settings/mercado-pago/oauth/callback").permitAll()
						.requestMatchers(org.springframework.http.HttpMethod.POST,
								"/api/v1/self-service/kiosk-sessions")
								.hasAuthority("selfservice.session.launch")
						.requestMatchers("/actuator/health/**", "/api/v1/system/**", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/self-service/**", "/api/v1/openapi/**", "/api/v1/docs/**", "/swagger-ui/**").permitAll()
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/organizations/*/logo").permitAll()
						.requestMatchers("/api/v1/platform/billing/**").hasAuthority("platform.admin")
						.requestMatchers("/api/v1/auth/select-branch").hasAuthority("branch.read")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/users/roles", "/api/v1/users/internal").hasAuthority("user.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/users/internal").hasAuthority("user.manage")
						.requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/users/internal/*/status", "/api/v1/users/internal/*/role").hasAuthority("user.manage")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/select-organization").hasAuthority("organization.read")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/organizations").hasAuthority("organization.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/organizations").hasAuthority("platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/organizations/*/status").hasAuthority("platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/organizations/current/branches").hasAuthority("branch.create")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/organizations/current/branches/*").hasAuthority("branch.update")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/organizations/current/branches", "/api/v1/organizations/current/branches/*", "/api/v1/organizations/current/branches/**")
								.hasAnyAuthority("branch.read", "organization.read", "inventory.manage", "inventory.transfer", "catalog.read")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/organizations/*/branches").hasAuthority("platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/organizations/current")
								.hasAnyAuthority("organization.read", "organization.update")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/organizations/current").hasAuthority("organization.update")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/organizations/current/logo").hasAuthority("organization.update")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/organizations/*/logo").hasAuthority("platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/onboarding/**").hasAuthority("onboarding.manage")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/onboarding/**").hasAuthority("onboarding.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/clients/*/workouts").hasAnyAuthority("workout.read.own", "workout.read.all")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/clients/imports").hasAuthority("client.import")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/clients/*/benefits").hasAnyAuthority("client.benefit.assign", "client.update", "client.create", "membership.update", "sale.create", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/clients/**")
								.hasAnyAuthority("client.read", "client.create", "client.update", "membership.read", "membership.create", "membership.update")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/clients")
								.hasAnyAuthority("client.create", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/clients/**").hasAuthority("client.update")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/catalog/**").hasAnyAuthority("catalog.read", "membership.read", "sale.create", "client.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/catalog/products/*/image").hasAuthority("catalog.image.manage")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/catalog/products/*/activate", "/api/v1/catalog/products/*/deactivate").hasAuthority("catalog.status.manage")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/catalog/products/*/restore").hasAuthority("catalog.archive")
						.requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/catalog/products/*/image").hasAuthority("catalog.image.manage")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/catalog/categories").hasAuthority("catalog.category.manage")
						.requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/catalog/categories/*").hasAuthority("catalog.category.manage")
						.requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/catalog/categories/*").hasAuthority("catalog.category.manage")
						.requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/catalog/products/*/status").hasAuthority("catalog.status.manage")
						.requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/catalog/products/*").hasAuthority("catalog.archive")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/catalog/products/*").hasAuthority("catalog.update")
						.requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/catalog/products/*").hasAuthority("catalog.update")
						.requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/catalog/plans/*").hasAuthority("catalog.update")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/catalog/**").hasAuthority("catalog.create")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/benefits/**").hasAnyAuthority("benefit.read", "client.read", "membership.read", "sale.create")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/benefits/**").hasAnyAuthority("benefit.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/client-benefits/*/verify").hasAuthority("client.benefit.verify")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/memberships/**").hasAuthority("membership.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/memberships/*/renew").hasAnyAuthority("membership.renew", "membership.update", "sale.create")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/memberships/*/change-plan").hasAnyAuthority("membership.change-plan", "membership.update", "sale.create")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/membership-quotes").hasAnyAuthority("membership.read", "membership.update", "sale.create")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/membership-enrollments").hasAnyAuthority("membership.create", "membership.update", "sale.create")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/sales/reports/**").hasAuthority("report.sales.read")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/sales/**").hasAuthority("sale.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sales").hasAuthority("sale.create")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sales/*/refunds").hasAuthority("sale.refund")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sales/*/payments").hasAuthority("payment.manual.create")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sales/payments/*/mercado-pago-order").hasAuthority("sale.create")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sales/payments/*/validate").hasAuthority("payment.manual.validate")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sales/payments/*/reject").hasAuthority("payment.manual.reject")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/sales/payments/*/receipt").hasAuthority("payment.receipt.upload")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/payment-settings/mercado-pago/terminals")
								.hasAnyAuthority("payment.settings.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.POST,
								"/api/v1/payment-settings/stripe/connect",
								"/api/v1/payment-settings/stripe/refresh")
								.hasAnyAuthority("payment.provider.stripe.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/payment-settings", "/api/v1/payment-settings/**")
								.hasAnyAuthority("payment.settings.read", "payment.settings.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/payment-settings/**")
								.hasAnyAuthority("payment.settings.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/payment-settings", "/api/v1/payment-settings/**")
								.hasAnyAuthority("payment.settings.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/access/visit-passes/**").hasAuthority("visit.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/access/visit-passes/*/consume").hasAuthority("visit.consume")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/access/visit-passes").hasAuthority("visit.create")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/access/clients/*/effective-benefits")
								.hasAnyAuthority("access.read", "client.read", "membership.read", "benefit.read")
						.requestMatchers(org.springframework.http.HttpMethod.GET,
								"/api/v1/access/physical-integration/candidates/**")
								.hasAnyAuthority("access.physical.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.POST,
								"/api/v1/access/devices")
								.hasAnyAuthority("access.physical.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.PUT,
								"/api/v1/access/devices/**")
								.hasAnyAuthority("access.physical.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.POST,
								"/api/v1/access/physical-integration/**")
								.hasAnyAuthority("access.physical.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.PUT,
								"/api/v1/access/physical-integration/**")
								.hasAnyAuthority("access.physical.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.DELETE,
								"/api/v1/access/physical-integration/**")
								.hasAnyAuthority("access.physical.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/access/**").hasAuthority("access.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/access/commands/open")
								.hasAnyAuthority("access.manual.open", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/access/**").hasAuthority("access.manage")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/access/**").hasAuthority("access.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/inventory/**").hasAuthority("inventory.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/inventory/**").hasAuthority("inventory.manage")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/inventory/**").hasAuthority("inventory.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/resources/**").hasAuthority("resource.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/resources/**").hasAuthority("resource.manage")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/resources/**").hasAuthority("resource.manage")
						.requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/resources/**").hasAuthority("resource.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/bookings/**").hasAuthority("booking.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/bookings/*/payments").hasAuthority("booking.payment.manage")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/bookings/**").hasAuthority("booking.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/coaches/profiles", "/api/v1/coaches/profiles/**").hasAuthority("coach.profile.manage")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/coaches/profiles", "/api/v1/coaches/profiles/**").hasAuthority("coach.profile.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/coaches/**").hasAuthority("coach.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/coaches/**").hasAuthority("coach.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/exercises/**").hasAuthority("exercise.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/exercises/**").hasAnyAuthority("exercise.create", "exercise.manage")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/exercises/**").hasAnyAuthority("exercise.create", "exercise.manage")
						.requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/exercises/**").hasAuthority("exercise.manage")
						.requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/exercises/**").hasAnyAuthority("exercise.create", "exercise.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/workouts/**").hasAnyAuthority("workout.read.own", "workout.read.all")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/workouts/**").hasAnyAuthority("workout.manage.own", "workout.manage.all")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/workouts/**").hasAnyAuthority("workout.manage.own", "workout.manage.all")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/workout-versions/**").hasAnyAuthority("workout.read.own", "workout.read.all")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/workout-assignments/*/pdf").hasAuthority("workout.pdf.download")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/workout-assignments/**").hasAnyAuthority("workout.read.own", "workout.read.all")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/workout-assignments").hasAuthority("workout.assign")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/workout-assignments/*/replace").hasAuthority("workout.assign")
						.requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/workout-assignments/**").hasAuthority("workout.assign")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/reports/coaches/workout-assignments/**").hasAuthority("coach.metrics.read")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/reports/coaches").hasAuthority("report.coaches.read")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/professionals/**").hasAnyAuthority("professional.read", "booking.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/professionals/**").hasAuthority("professional.manage")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/professionals/**").hasAuthority("professional.manage")
						.requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/v1/professionals/**").hasAuthority("professional.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/wellness/**").hasAnyAuthority("professional.read", "booking.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/wellness/**").hasAnyAuthority("professional.manage", "booking.manage")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/wellness/**").hasAnyAuthority("professional.manage", "booking.manage")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/notifications/email-configuration")
								.hasAnyAuthority("notification.settings.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/notifications/email-configuration/test")
								.hasAnyAuthority("notification.settings.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/notifications/email-preferences")
								.hasAnyAuthority("client.update", "notification.manage", "notification.settings.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/notifications/email-preferences")
								.hasAnyAuthority("client.update", "notification.manage", "notification.settings.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/notifications/email-configuration")
								.hasAnyAuthority("notification.read", "notification.settings.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/notifications/**").hasAuthority("notification.read")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/notifications/**").hasAuthority("notification.manage")
						.requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/notifications/**").hasAuthority("notification.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/automations/**").hasAuthority("automation.read")
						.requestMatchers("/api/v1/kiosk-settings/**")
								.hasAnyAuthority("selfservice.settings.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/automations/scheduler/**")
								.hasAnyAuthority("automation.scheduler.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/automations/*/execute")
								.hasAnyAuthority("automation.execute", "automation.manage", "platform.admin")
						.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/automations/**").hasAuthority("automation.manage")
						.requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/reports/owner-dashboard").hasAuthority("report.owner.read")
						.anyRequest().authenticated())
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(new StandardAuthenticationEntryPoint(objectMapper))
						.accessDeniedHandler(new StandardAccessDeniedHandler(objectMapper)))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).build();
	}

	@Bean FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(JwtAuthenticationFilter filter) { FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter); registration.setEnabled(false); return registration; }
	@Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
	@Bean AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) { DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService); provider.setPasswordEncoder(passwordEncoder); return new ProviderManager(provider); }
	@Bean CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) { return corsConfigurationSource(properties.corsAllowedOrigins()); }
	private CorsConfigurationSource corsConfigurationSource(List<String> allowedOrigins) { CorsConfiguration configuration = new CorsConfiguration(); configuration.setAllowedOrigins(allowedOrigins); configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")); configuration.setAllowedHeaders(List.of("*")); configuration.setExposedHeaders(List.of("X-Correlation-Id")); configuration.setAllowCredentials(true); UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", configuration); return source; }
}
