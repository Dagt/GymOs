package com.gymos;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.jdbc.core.simple.JdbcClient;

class ArchitectureTests {
	private final JavaClasses classes = new ClassFileImporter().importPackages("com.gymos");

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
	void jdbcClientUsageInApplicationIsExplicitlyTracked() {
		Set<String> allowedApplicationServices = Set.of(
				"com.gymos.catalog.application.CatalogService",
				"com.gymos.clients.application.ClientService",
				"com.gymos.memberships.application.MembershipService",
				"com.gymos.organizations.application.OrganizationService",
				"com.gymos.sales.application.SalesService",
				"com.gymos.selfservice.application.SelfServiceService"
		);

		Set<String> actual = classes.stream()
				.filter(javaClass -> javaClass.getPackageName().contains(".application"))
				.filter(javaClass -> javaClass.getDirectDependenciesFromSelf().stream()
						.anyMatch(dependency -> dependency.getTargetClass().isEquivalentTo(JdbcClient.class)))
				.map(javaClass -> javaClass.getName())
				.collect(java.util.stream.Collectors.toSet());

		assertThat(actual).isSubsetOf(allowedApplicationServices);
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
