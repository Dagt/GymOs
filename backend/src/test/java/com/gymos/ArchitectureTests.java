package com.gymos;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTests {
	private final JavaClasses classes = new ClassFileImporter()
			.withImportOption(new ImportOption.DoNotIncludeTests())
			.importPackages("com.gymos");

	@Test
	void verifiesModulithBoundaries() {
		ApplicationModules.of(GymOsApplication.class).verify();
	}

	@Test
	void applicationLayerDoesNotDependOnWebControllers() {
		noClasses()
				.that().resideInAPackage("..application..")
				.should().dependOnClassesThat().haveSimpleNameEndingWith("Controller")
				.check(classes);
	}

	@Test
	void modulesDoNotDependOnOtherModulesInfrastructure() {
		var violations = classes.stream()
				.flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
				.filter(dependency -> moduleOf(dependency.getOriginClass().getPackageName()) != null)
				.filter(dependency -> dependency.getTargetClass().getPackageName().contains(".infrastructure"))
				.filter(dependency -> {
					String originModule = moduleOf(dependency.getOriginClass().getPackageName());
					String targetModule = moduleOf(dependency.getTargetClass().getPackageName());
					return targetModule != null && !targetModule.equals(originModule);
				})
				.map(dependency -> dependency.getOriginClass().getName() + " -> " + dependency.getTargetClass().getName())
				.sorted()
				.toList();

		assertThat(violations).isEmpty();
	}

	@Test
	void controllersDoNotDependOnInfrastructureOfOtherModules() {
		var violations = classes.stream()
				.filter(javaClass -> javaClass.getPackageName().contains(".api"))
				.flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
				.filter(dependency -> dependency.getTargetClass().getPackageName().contains(".infrastructure"))
				.filter(dependency -> {
					String originModule = moduleOf(dependency.getOriginClass().getPackageName());
					String targetModule = moduleOf(dependency.getTargetClass().getPackageName());
					return targetModule != null && !targetModule.equals(originModule);
				})
				.map(dependency -> dependency.getOriginClass().getName() + " -> " + dependency.getTargetClass().getName())
				.sorted()
				.toList();

		assertThat(violations).isEmpty();
	}

	@Test
	void jdbcClientUsageInApplicationMatchesTheExplicitAllowlist() {
		Set<String> allowedApplicationServices = Set.of(
				"com.gymos.catalog.application.CatalogService",
				"com.gymos.clients.application.ClientImportService",
				"com.gymos.clients.application.ClientService",
				"com.gymos.onboarding.application.OnboardingService",
				"com.gymos.organizations.application.OrganizationService",
				"com.gymos.sales.application.SalesService"
		);

		Set<String> actual = classes.stream()
				.filter(javaClass -> javaClass.getPackageName().contains(".application"))
				.filter(javaClass -> javaClass.getDirectDependenciesFromSelf().stream()
						.anyMatch(dependency -> dependency.getTargetClass().isEquivalentTo(JdbcClient.class)))
				.map(javaClass -> javaClass.getName())
				.collect(java.util.stream.Collectors.toSet());

		assertThat(actual).isEqualTo(allowedApplicationServices);
	}

	@Test
	void selfServiceUsesOnlyPublicApisFromOtherModules() {
		var violations = classes.stream()
				.filter(javaClass -> javaClass.getPackageName().startsWith("com.gymos.selfservice."))
				.flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
				.filter(dependency -> {
					String targetPackage = dependency.getTargetClass().getPackageName();
					return targetPackage.startsWith("com.gymos.memberships.application")
							|| targetPackage.startsWith("com.gymos.clients.application")
							|| targetPackage.startsWith("com.gymos.clients.infrastructure");
				})
				.map(dependency -> dependency.getOriginClass().getName() + " -> " + dependency.getTargetClass().getName())
				.sorted()
				.toList();

		assertThat(violations).isEmpty();
	}

	@Test
	void commonDoesNotOwnBusinessEvents() {
		var commonEvents = classes.stream()
				.map(javaClass -> javaClass.getPackageName())
				.filter(packageName -> packageName.startsWith("com.gymos.common.events"))
				.distinct()
				.toList();

		assertThat(commonEvents).isEmpty();
	}

	private String moduleOf(String packageName) {
		if (!packageName.startsWith("com.gymos.")) {
			return null;
		}
		String remainder = packageName.substring("com.gymos.".length());
		int dot = remainder.indexOf('.');
		return dot < 0 ? remainder : remainder.substring(0, dot);
	}
}
