package com.gymos.reporting.application;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.reporting.application.result.OwnerDashboardResult;
import com.gymos.reporting.infrastructure.OwnerDashboardRepository.OwnerDashboardRecord;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OwnerDashboardArchitectureTests {
	private final JavaClasses classes = new ClassFileImporter().importPackages("com.gymos.reporting");

	@Test
	void reportingApiDoesNotExposeInfrastructureTypes() {
		classes()
				.that().resideInAPackage("..reporting.api..")
				.should().onlyDependOnClassesThat().resideOutsideOfPackage("..reporting.infrastructure..")
				.check(classes);
	}

	@Test
	void ownerDashboardServiceReturnsApplicationResult() throws NoSuchMethodException {
		assertThat(OwnerDashboardService.class
				.getMethod("dashboard", GymOsPrincipal.class, LocalDate.class, LocalDate.class)
				.getReturnType())
				.isEqualTo(OwnerDashboardResult.class)
				.isNotEqualTo(OwnerDashboardRecord.class);
	}
}
