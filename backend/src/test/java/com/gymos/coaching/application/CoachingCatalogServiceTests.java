package com.gymos.coaching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.coaching.application.CoachingCatalogService.CreateExerciseCommand;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.AssignmentDetail;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.AssignmentPdfData;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.AssignmentStats;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.ExerciseRecord;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository.WeeklyTrendMetric;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoachingCatalogServiceTests {
	private CoachingCatalogRepository repository;
	private CoachingCatalogService service;
	private GymOsPrincipal principal;

	@BeforeEach
	void setUp() {
		repository = mock(CoachingCatalogRepository.class);
		service = new CoachingCatalogService(repository, mock(AuditService.class), new ObjectMapper());
		principal = new GymOsPrincipal(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"coach", "Coach", Set.of("exercise.read", "exercise.create", "workout.read.all")
		);
	}

	@Test
	void exerciseNameIsNormalizedAndCoachOriginIsUsed() {
		UUID exerciseId = UUID.randomUUID();
		ExerciseRecord created = exercise(exerciseId, "Press Inclinado", "COACH");
		when(repository.createExercise(eq(principal.organizationId()), eq(principal.userId()), any()))
				.thenReturn(exerciseId);
		when(repository.exercise(principal.organizationId(), exerciseId)).thenReturn(Optional.of(created));

		ExerciseRecord result = service.createExercise(
				principal,
				new CreateExerciseCommand(
						"  Press Inclinado ", null, null, "Pecho", null,
						"Empuje", "strength", "Barra", null, null, null, null
				),
				"test"
		);

		assertThat(result.name()).isEqualTo("Press Inclinado");
		verify(repository).createExercise(eq(principal.organizationId()), eq(principal.userId()), any());
	}

	@Test
	void duplicateExerciseIsRejectedAcrossSystemAndTenantCatalog() {
		when(repository.activeByNormalizedName(principal.organizationId(), "press inclinado"))
				.thenReturn(Optional.of(exercise(UUID.randomUUID(), "Press inclinado", "SYSTEM")));

		assertThatThrownBy(() -> service.createExercise(
				principal,
				new CreateExerciseCommand(
						"Press Inclinado", null, null, null, null,
						null, "STRENGTH", null, null, null, null, null
				),
				"test"
		))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("Ya existe");
	}

	@Test
	void systemExerciseCannotBeEditedOrReceiveAliases() {
		UUID exerciseId = UUID.randomUUID();
		when(repository.exercise(principal.organizationId(), exerciseId))
				.thenReturn(Optional.of(exercise(exerciseId, "Press inclinado", "SYSTEM")));

		assertThatThrownBy(() -> service.updateExercise(
				principal, exerciseId,
				new CreateExerciseCommand(
						"Press inclinado", null, null, null, null,
						null, "STRENGTH", null, null, null, null, null
				), "test"
		)).isInstanceOf(BusinessException.class).hasMessageContaining("solo lectura");

		assertThatThrownBy(() -> service.addAlias(principal, exerciseId, "Press superior", "test"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("solo lectura");
	}

	@Test
	void aliasIsNormalizedBeforePersistence() {
		UUID exerciseId = UUID.randomUUID();
		when(repository.exercise(principal.organizationId(), exerciseId))
				.thenReturn(Optional.of(exercise(exerciseId, "Press inclinado", "COACH")));
		when(repository.aliases(exerciseId)).thenReturn(List.of("Press superior"));
		when(repository.exerciseCreatedBy(principal.organizationId(), exerciseId, principal.userId()))
				.thenReturn(true);

		List<String> aliases = service.addAlias(principal, exerciseId, "  Préss   Superior ", "test");

		assertThat(aliases).containsExactly("Press superior");
		verify(repository).addAlias(exerciseId, "Préss   Superior", "press superior");
	}

	@Test
	void reportIncludesActiveCoachesWithoutAssignments() {
		var coachId = UUID.randomUUID();
		when(repository.metrics(
				eq(principal.organizationId()), eq(principal.branchId()), any(), any(), any(), any(), any()
		))
				.thenReturn(List.of(new CoachingCatalogRepository.CoachMetric(coachId, "Coach Uno", 0, 0, 0)));
		when(repository.assignments(
				eq(principal.organizationId()), eq(principal.branchId()), any(), any(),
				any(), any(), any(), anyInt(), anyInt()
		))
				.thenReturn(List.of());
		when(repository.assignmentStats(
				eq(principal.organizationId()), eq(principal.branchId()), any(), any(), any(), any(), any()
		)).thenReturn(new AssignmentStats(0, 0, 0));

		var report = service.assignmentReport(principal, null, null, null, null);

		assertThat(report.activeCoaches()).isEqualTo(1);
		assertThat(report.coachesWithoutAssignments()).isEqualTo(1);
		assertThat(report.assignments()).isZero();
	}

	@Test
	void reportUsesTheVisibleAssignmentsAndComparesAgainstPreviousPeriod() {
		UUID coachId = UUID.randomUUID();
		UUID clientOne = UUID.randomUUID();
		UUID clientTwo = UUID.randomUUID();
		LocalDate from = LocalDate.of(2026, 7, 20);
		LocalDate to = LocalDate.of(2026, 7, 26);
		when(repository.metrics(
				principal.organizationId(), principal.branchId(), from, to, null, null, null
		)).thenReturn(List.of(new CoachingCatalogRepository.CoachMetric(coachId, "Coach Uno", 1, 1, 1)));
		when(repository.assignments(
				eq(principal.organizationId()), eq(principal.branchId()), any(), any(),
				any(), any(), any(), anyInt(), anyInt()
		)).thenReturn(
				List.of(
						assignment(clientOne, coachId, "ACTIVE", from),
						assignment(clientTwo, coachId, "CANCELLED", from)
				)
		);
		when(repository.assignmentStats(
				eq(principal.organizationId()), eq(principal.branchId()), any(), any(), any(), any(), any()
		)).thenReturn(
				new AssignmentStats(2, 1, 1),
				new AssignmentStats(2, 2, 2)
		);

		var report = service.assignmentReport(principal, from, to, null, null);

		assertThat(report.assignments()).isEqualTo(1);
		assertThat(report.uniqueClients()).isEqualTo(1);
		assertThat(report.previousAssignments()).isEqualTo(2);
		assertThat(report.previousUniqueClients()).isEqualTo(2);
		assertThat(report.assignmentChange()).isEqualTo(-1);
		assertThat(report.uniqueClientChange()).isEqualTo(-1);
	}

	@Test
	void coachReportIsRestrictedToTheAuthenticatedCoach() {
		GymOsPrincipal coach = new GymOsPrincipal(
				principal.userId(), principal.organizationId(), principal.branchId(),
				"coach", "Coach", Set.of("workout.read.own")
		);
		when(repository.metrics(
				eq(coach.organizationId()), eq(coach.branchId()), any(), any(),
				any(), any(), eq(coach.userId())
		)).thenReturn(List.of());
		when(repository.assignments(
				eq(coach.organizationId()), eq(coach.branchId()), any(), any(),
				any(), any(), eq(coach.userId()), anyInt(), anyInt()
		)).thenReturn(List.of());
		when(repository.assignmentStats(
				eq(coach.organizationId()), eq(coach.branchId()), any(), any(),
				any(), any(), eq(coach.userId())
		)).thenReturn(new AssignmentStats(0, 0, 0));

		service.assignmentReport(coach, LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), null, null);

		verify(repository).metrics(
				eq(coach.organizationId()), eq(coach.branchId()), any(), any(),
				any(), any(), eq(coach.userId())
		);
	}

	@Test
	void weeklyTrendFillsWeeksWithoutAssignmentsAndKeepsClientFilter() {
		UUID clientId = UUID.randomUUID();
		UUID coachId = UUID.randomUUID();
		LocalDate to = LocalDate.of(2026, 7, 26);
		LocalDate firstWeek = LocalDate.of(2026, 6, 29);
		when(repository.weeklyTrend(
				principal.organizationId(), principal.branchId(), firstWeek, to,
				coachId, clientId, null
		)).thenReturn(List.of(
				new WeeklyTrendMetric(firstWeek.plusWeeks(1), 3, 1, 1),
				new WeeklyTrendMetric(firstWeek.plusWeeks(3), 2, 1, 0)
		));

		var trend = service.weeklyTrend(principal, 4, to, coachId, clientId);

		assertThat(trend.from()).isEqualTo(firstWeek);
		assertThat(trend.to()).isEqualTo(to);
		assertThat(trend.points()).extracting(point -> point.assignments())
				.containsExactly(0L, 3L, 0L, 2L);
		assertThat(trend.points().getLast().weekTo()).isEqualTo(to);
	}

	@Test
	void weeklyTrendRejectsUnsupportedRange() {
		assertThatThrownBy(() -> service.weeklyTrend(principal, 53, LocalDate.now(), null, null))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("entre 4 y 52");
	}

	@Test
	void assignmentPdfContainsBrandedStructuredWorkout() throws Exception {
		UUID assignmentId = UUID.randomUUID();
		String content = """
				{"sessions":[{"name":"Pierna","notes":"Calentamiento articular","blocks":[{
				"name":"Trabajo principal","exercises":[{"exerciseName":"Sentadilla libre",
				"sets":4,"reps":"8-10","load":"RPE 8","restSeconds":90,"tempo":"3-1-1",
				"prescription":"4 series progresivas","notes":"Mantener espalda neutra"}]}]}]}
				""";
		when(repository.assignmentPdf(
				principal.organizationId(), principal.branchId(), assignmentId, null
		)).thenReturn(Optional.of(new AssignmentPdfData(
				assignmentId, LocalDate.of(2026, 7, 26), LocalDate.of(2026, 7, 28),
				LocalDate.of(2026, 8, 25), "Subir carga sólo con técnica correcta",
				"Fuerza base", "Hipertrofia", "INTERMEDIATE", 3, content,
				"Diego García", "Coach Uno", "GymOS Demo", null,
				"#173c2a", "Sucursal Centro", "555-0100"
		)));

		byte[] pdf = service.assignmentPdf(principal, assignmentId);

		assertThat(pdf).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
		try (var document = Loader.loadPDF(pdf)) {
			String text = new PDFTextStripper().getText(document);
			assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
			assertThat(text).contains("GymOS Demo", "Fuerza base", "Diego García", "Sentadilla libre");
			assertThat(text).contains("RPE 8", "90 s", "3-1-1");
		}
	}

	private ExerciseRecord exercise(UUID id, String name, String origin) {
		return new ExerciseRecord(
				id, "SYSTEM".equals(origin) ? null : principal.organizationId(), name, null, null,
				"Pecho", null, "Empuje", "STRENGTH", "Barra", null, null,
				null, null, origin, "ACTIVE", principal.userId(), OffsetDateTime.now(), OffsetDateTime.now()
		);
	}

	private AssignmentDetail assignment(UUID clientId, UUID coachId, String status, LocalDate assignedOn) {
		return new AssignmentDetail(
				UUID.randomUUID(), assignedOn, status, null, null, null, null,
				UUID.randomUUID(), "Fuerza base", 1, clientId, "Socio Uno",
				coachId, "Coach Uno", null, null
		);
	}
}
