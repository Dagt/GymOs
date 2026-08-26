package com.gymos;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymos.audit.api.AuditService;
import com.gymos.benefits.api.events.AccessBenefitConfigurationChangedV1;
import com.gymos.benefits.application.BenefitService;
import com.gymos.benefits.application.command.CreateBenefitCommand;
import com.gymos.benefits.application.command.UpdateBenefitCommand;
import com.gymos.benefits.infrastructure.BenefitRepository;
import com.gymos.coaching.api.operations.CoachProfileProvisioning;
import com.gymos.coaching.application.AssignmentExpirationWorker;
import com.gymos.coaching.application.CoachingService;
import com.gymos.coaching.application.command.AssignWorkoutCommand;
import com.gymos.coaching.infrastructure.CoachingCatalogRepository;
import com.gymos.coaching.infrastructure.CoachingRepository;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.common.security.StrongPasswordPolicy;
import com.gymos.common.security.UserCredentialVersionRepository;
import com.gymos.common.time.BusinessTime;
import com.gymos.identity.application.*;
import com.gymos.identity.infrastructure.PasswordRecoveryRepository;
import com.gymos.identity.infrastructure.UserAdministrationRepository;
import com.gymos.notifications.api.operations.PasswordRecoveryEmailDelivery;
import com.gymos.security.application.GymOsUserDetailsService;
import com.gymos.security.infrastructure.AuthQueryRepository;
import com.gymos.support.OperationalTestData;
import com.gymos.support.OperationalTestData.Workout;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres-integration")
@Testcontainers(disabledWithoutDocker = true)
class OperationalHardeningPostgresIntegrationTests {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");
    private static DriverManagerDataSource dataSource;
    private JdbcClient jdbc;
    private OperationalTestData data;
    private TransactionTemplate transactions;
    private DataSourceTransactionManager transactionManager;
    private AuditService audit;
    private CoachingRepository coaching;
    private CoachingService workouts;
    private CoachingCatalogRepository metrics;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private PasswordRecoveryEmailDelivery emails;
    private PasswordRecoveryService recovery;
    private UserAdministrationRepository users;

    @BeforeAll static void migrate() {
        dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach void setUp() {
        jdbc = JdbcClient.create(dataSource);
        data = new OperationalTestData(jdbc);
        transactionManager = new DataSourceTransactionManager(dataSource);
        transactions = new TransactionTemplate(transactionManager);
        audit = mock(AuditService.class);
        coaching = new CoachingRepository(jdbc);
        workouts = new CoachingService(coaching, audit, new ObjectMapper());
        metrics = new CoachingCatalogRepository(jdbc);
        users = new UserAdministrationRepository(jdbc);
        emails = mock(PasswordRecoveryEmailDelivery.class);
        when(emails.deliver(any())).thenReturn("SENT");
        recovery = new PasswordRecoveryService(new PasswordRecoveryRepository(jdbc),
                new UserAdministrationService(users, encoder, audit, mock(CoachProfileProvisioning.class)),
                encoder, new StrongPasswordPolicy(), new PasswordRecoveryProperties(60, 60, 10),
                new IdentityProperties(72, 60, "https://test.example.invalid"), emails, audit, transactionManager);
    }

    @Test void assignmentRequiresCurrentDatesAndAnActiveBranchMember() {
        Workout workout = data.workout(data.coach());
        UUID client = data.client();
        assertCode(() -> assign(workout, client, null, null), "WORKOUT_ASSIGNMENT_DATES_REQUIRED");
        assertCode(() -> assign(workout, client, today().minusDays(1), today()), "WORKOUT_ASSIGNMENT_START_IN_PAST");
        assertCode(() -> assign(workout, client, today(), today().minusDays(1)), "WORKOUT_ASSIGNMENT_RANGE_INVALID");
        jdbc.sql("UPDATE client SET status = 'INACTIVE' WHERE id = :id").param("id", client).update();
        assertCode(() -> assign(workout, client, today(), today().plusDays(28)), "CLIENT_NOT_FOUND");
    }

    @Test void duplicateAndOverlappingAssignmentsAreRejectedButNextPeriodCanBeScheduled() {
        Workout workout = data.workout(data.coach());
        UUID client = data.client();
        var active = assign(workout, client, today(), today().plusDays(28));
        assertThat(active.assignedOn()).isEqualTo(today());
        assertCode(() -> assign(workout, client, today(), today().plusDays(28)), "WORKOUT_ASSIGNMENT_OVERLAP");
        assertCode(() -> assign(workout, client, today().plusDays(28), today().plusDays(40)), "WORKOUT_ASSIGNMENT_OVERLAP");
        var scheduled = assign(workout, client, today().plusDays(29), today().plusDays(56));
        assertThat(scheduled.status()).isEqualTo("SCHEDULED");
        assertCode(() -> tx(() -> workouts.updateAssignmentStatus(data.owner, scheduled.id(), "COMPLETED", null, "test")), "WORKOUT_ASSIGNMENT_TRANSITION_INVALID");
        assertThat(coaching.coachReport(data.organizationId, data.branchId).activeAssignments()).isEqualTo(1);
    }

    @Test void concurrentSubmissionsCannotBypassTheOverlapRule() throws Exception {
        Workout workout = data.workout(data.coach()); UUID client = data.client();
        List<String> results = concurrent(() -> {
            try { assign(workout, client, today(), today().plusDays(28)); return "CREATED"; }
            catch (BusinessException error) { return error.code(); }
        });
        assertThat(results).containsExactlyInAnyOrder("CREATED", "WORKOUT_ASSIGNMENT_OVERLAP");
        assertThat(coaching.clientAssignments(data.organizationId, data.branchId, client, null)).hasSize(1);
    }

    @Test void explicitReplacementKeepsHistoryAndIdempotentRetriesDoNotCreateDuplicates() {
        Workout workout = data.workout(data.coach()); UUID client = data.client();
        var previous = assign(workout, client, today(), today().plusDays(28));
        AssignWorkoutCommand command = command(workout, client, today(), today().plusDays(35), "replacement-key");
        var replacement = tx(() -> workouts.replaceAssignment(data.owner, previous.id(), workout.id(), command, "test"));
        assertThat(tx(() -> workouts.replaceAssignment(data.owner, previous.id(), workout.id(), command, "test")).id()).isEqualTo(replacement.id());
        assertThat(coaching.assignment(data.organizationId, data.branchId, previous.id(), null).orElseThrow().status()).isEqualTo("REPLACED");
        assertThat(coaching.clientAssignments(data.organizationId, data.branchId, client, null)).hasSize(2);
        assertCode(() -> tx(() -> workouts.updateAssignmentStatus(data.owner, previous.id(), "ACTIVE", null, "test")), "WORKOUT_ASSIGNMENT_STATUS_INVALID");
    }

    @Test void expiryIsVisibleBeforeWorkerAndPersistedExactlyOnceWithInclusiveEndDate() {
        Workout workout = data.workout(data.coach());
        var ended = assign(workout, data.client(), today(), today());
        var stillActive = assign(workout, data.client(), today(), today());
        jdbc.sql("UPDATE workout_assignment SET starts_on = :start, ends_on = :end WHERE id = :id")
                .param("start", today().minusDays(8)).param("end", today().minusDays(1)).param("id", ended.id()).update();
        assertThat(coaching.assignment(data.organizationId, data.branchId, ended.id(), null).orElseThrow().status()).isEqualTo("COMPLETED");
        assertThat(coaching.assignment(data.organizationId, data.branchId, stillActive.id(), null).orElseThrow().status()).isEqualTo("ACTIVE");
        var worker = new AssignmentExpirationWorker(coaching, audit, 250);
        transactions.executeWithoutResult(status -> worker.completeDueAssignments());
        transactions.executeWithoutResult(status -> worker.completeDueAssignments());
        assertThat(jdbc.sql("SELECT status FROM workout_assignment WHERE id = :id").param("id", ended.id()).query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT version FROM workout_assignment WHERE id = :id").param("id", ended.id()).query(Long.class).single()).isEqualTo(1);
        verify(audit, times(1)).recordSystem(data.organizationId, data.branchId, null, "WORKOUT_ASSIGNMENT_PERIOD_ENDED", "WORKOUT_ASSIGNMENT", ended.id(), "SCHEDULER", "PERIOD_ENDED", "assignment-expiration");
        assertThat(metrics.assignments(data.organizationId, data.branchId, today(), today(), null, null, null, 25, 0))
                .filteredOn(row -> row.id().equals(ended.id())).singleElement().satisfies(row -> assertThat(row.statusReason()).isEqualTo("PERIOD_ENDED"));
    }

    @Test void weeklyAndDailyMetricsCountSameDayFirstsOnceAndNeverMultiplyByWorkoutCount() {
        UUID coach = data.coach(); Workout workout = data.workout(coach); data.workout(coach);
        UUID client = data.client();
        assign(workout, client, today(), today().plusDays(10));
        assign(workout, client, today().plusDays(11), today().plusDays(20));
        assign(workout, data.client(), today(), today().plusDays(10));
        var cancelled = assign(workout, data.client(), today(), today().plusDays(10));
        tx(() -> workouts.updateAssignmentStatus(data.owner, cancelled.id(), "CANCELLED", "Test", "test"));
        var summary = coaching.coachReport(data.organizationId, data.branchId);
        assertThat(summary.workoutCount()).isEqualTo(2);
        assertThat(summary.activeAssignments()).isEqualTo(2);
        assertThat(summary.assignmentsLast30Days()).isEqualTo(3);
        assertThat(metrics.weeklyTrend(data.organizationId, data.branchId, today().minusDays(7), today(), null, null, null))
                .singleElement().satisfies(point -> { assertThat(point.assignments()).isEqualTo(3); assertThat(point.firstWorkouts()).isEqualTo(2); assertThat(point.uniqueClients()).isEqualTo(2); });
        assertThat(metrics.coachActivity(data.organizationId, data.branchId, today(), today(), null, null, null))
                .singleElement().satisfies(point -> { assertThat(point.date()).isEqualTo(today()); assertThat(point.assignments()).isEqualTo(3); assertThat(point.firstWorkouts()).isEqualTo(2); });
        assertThat(metrics.metrics(data.organizationId, data.branchId, today(), today(), null, null, null))
                .singleElement().satisfies(point -> assertThat(point.firstWorkouts()).isEqualTo(2));
    }

    @Test void metricsAndAssignmentHistoryNeverLeakAnotherBranchTenantOrCoach() {
        UUID coach = data.coach(); Workout workout = data.workout(coach); UUID client = data.client();
        assign(workout, client, today(), today().plusDays(7));
        assertThat(metrics.coachActivity(UUID.randomUUID(), data.branchId, today(), today(), null, null, null)).isEmpty();
        assertThat(metrics.coachActivity(data.organizationId, UUID.randomUUID(), today(), today(), null, null, null)).isEmpty();
        assertThat(metrics.coachActivity(data.organizationId, data.branchId, today(), today(), null, null, UUID.randomUUID())).isEmpty();
        assertThat(metrics.coachActivity(data.organizationId, data.branchId, today().minusDays(7), today().minusDays(1), null, null, null)).isEmpty();
        assertThat(metrics.assignments(data.organizationId, data.branchId, today(), today(), null, null, UUID.randomUUID(), 25, 0)).isEmpty();
    }

    @Test void existingSaunaCanAddDailyVipPlanWithoutChangingBenefitIdentityAndUsesOptimisticLock() {
        UUID vip = data.plan("VIP", 30), daily = data.plan("VIP diario", 1);
        var events = mock(ApplicationEventPublisher.class);
        var repository = new BenefitRepository(jdbc);
        var service = new BenefitService(repository, audit, events);
        var benefit = tx(() -> service.createBenefit(data.owner, new CreateBenefitCommand(null, "SAUNA", "Sauna", "ACCESS", "NONE", null, null, false, false, 10, null, null, "ACTIVE", List.of(vip)), "test"));
        jdbc.sql("UPDATE membership_plan SET status = 'INACTIVE' WHERE id = :id").param("id", vip).update();
        var update = new UpdateBenefitCommand("Sauna VIP", "NONE", null, null, false, false, 10, null, null, "ACTIVE", List.of(vip, daily), benefit.version());
        var updated = tx(() -> service.updateBenefit(data.owner, benefit.id(), update, "test"));
        assertThat(updated.id()).isEqualTo(benefit.id()); assertThat(updated.code()).isEqualTo("SAUNA");
        assertThat(updated.planIds()).containsExactlyInAnyOrder(vip, daily); assertThat(updated.version()).isEqualTo(1);
        assertCode(() -> tx(() -> service.updateBenefit(data.owner, benefit.id(), update, "test")), "BENEFIT_VERSION_CONFLICT");
        tx(() -> service.updateBenefit(data.owner, benefit.id(), new UpdateBenefitCommand("Sauna", "NONE", null, null, false, false, 10, null, null, "ACTIVE", List.of(daily), 1), "test"));
        var event = ArgumentCaptor.forClass(AccessBenefitConfigurationChangedV1.class);
        verify(events, times(3)).publishEvent(event.capture());
        assertThat(event.getAllValues().getLast().planIds()).containsExactlyInAnyOrder(vip, daily);
        assertThat(repository.benefits(data.organizationId)).hasSize(1);
    }

    @Test void editingBenefitCannotLinkUnavailablePlansOrCrossTenantAndBranchBoundaries() {
        UUID vip = data.plan("VIP", 30), retired = data.plan("Retirado", 30);
        var repository = new BenefitRepository(jdbc);
        var service = new BenefitService(repository, audit, mock(ApplicationEventPublisher.class));
        var benefit = tx(() -> service.createBenefit(data.owner, new CreateBenefitCommand(null, "SAUNA", "Sauna", "ACCESS", "NONE", null, null, false, false, 10, null, null, "ACTIVE", List.of(vip)), "test"));
        jdbc.sql("UPDATE membership_plan SET status = 'INACTIVE' WHERE id = :id").param("id", retired).update();
        var update = new UpdateBenefitCommand("Sauna", "NONE", null, null, false, false, 10, null, null, "ACTIVE", List.of(vip, retired), 0);
        assertCode(() -> tx(() -> service.updateBenefit(data.owner, benefit.id(), update, "test")), "MEMBERSHIP_PLAN_NOT_AVAILABLE");
        var foreign = new OperationalTestData(jdbc);
        assertCode(() -> tx(() -> service.updateBenefit(foreign.owner, benefit.id(), update, "test")), "BENEFIT_NOT_FOUND");
        var otherBranch = new GymOsPrincipal(data.ownerId, data.organizationId, UUID.randomUUID(), "owner", "Dueño", Set.of("benefit.manage"), Set.of("Dueno del gimnasio"), false);
        assertCode(() -> tx(() -> service.updateBenefit(otherBranch, benefit.id(), update, "test")), "BENEFIT_BRANCH_MISMATCH");
        var viewer = new GymOsPrincipal(data.ownerId, data.organizationId, data.branchId, "viewer", "Consulta", Set.of("benefit.read"), Set.of("Recepcion"), false);
        assertThatThrownBy(() -> tx(() -> service.updateBenefit(viewer, benefit.id(), update, "test")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(repository.benefit(data.organizationId, benefit.id()).orElseThrow().planIds()).containsExactly(vip);
        assertThat(repository.benefit(data.organizationId, benefit.id()).orElseThrow().version()).isZero();
    }

    @Test void staffRecoveryStoresOnlyHashAndNewPasswordInvalidatesOldCredentials() {
        UUID staff = data.user("Recepcion", encoder.encode("OldPassword12!"));
        var issued = recovery.issue(data.owner, staff, "test"); String token = token(issued);
        assertThat(issued.emailDeliveryStatus()).isEqualTo("SENT");
        String storedHash = jdbc.sql("SELECT token_hash FROM user_password_reset WHERE user_id = :id").param("id", staff).query(String.class).single();
        assertThat(storedHash).isEqualTo(UserInvitationService.sha256(token)).doesNotContain(token);
        assertThat(issued.toString()).doesNotContain(token);
        recovery.consume(token, "NewPassword123!", "test");
        var user = new AuthQueryRepository(jdbc).findActiveUserById(staff).orElseThrow();
        assertThat(encoder.matches("NewPassword123!", user.passwordHash())).isTrue();
        assertThat(encoder.matches("OldPassword12!", user.passwordHash())).isFalse();
        assertThat(new UserCredentialVersionRepository(jdbc).isCurrent(staff, 0)).isFalse();
        assertThat(new UserCredentialVersionRepository(jdbc).isCurrent(staff, 1)).isTrue();
        var auth = new DaoAuthenticationProvider(new GymOsUserDetailsService(new AuthQueryRepository(jdbc))); auth.setPasswordEncoder(encoder);
        assertThatCode(() -> auth.authenticate(new UsernamePasswordAuthenticationToken(staff.toString(), "NewPassword123!"))).doesNotThrowAnyException();
        assertCode(() -> { recovery.consume(token, "OtherPassword123!", "test"); return null; }, "PASSWORD_RESET_INVALID");
        assertThat(mockingDetails(audit).getInvocations().toString()).doesNotContain(token, "NewPassword123!", "OldPassword12!");
        var email = ArgumentCaptor.forClass(PasswordRecoveryEmailDelivery.RecoveryEmail.class);
        verify(emails).deliver(email.capture()); assertThat(email.getValue().toString()).doesNotContain(token);
    }

    @Test void recoveryCanBeReissuedAfterCooldownAndExpiredRevokedAndUnknownTokensAreIndistinguishable() {
        UUID staff = data.user("Coach", "hash");
        var first = recovery.issue(data.owner, staff, "test");
        assertCode(() -> recovery.issue(data.owner, staff, "test"), "PASSWORD_RESET_COOLDOWN");
        jdbc.sql("UPDATE user_password_reset SET created_at = now() - interval '2 minutes' WHERE user_id = :id").param("id", staff).update();
        when(emails.deliver(any())).thenReturn("FAILED");
        var next = recovery.issue(data.owner, staff, "test");
        assertThat(next.emailDeliveryStatus()).isEqualTo("FAILED");
        assertCode(() -> { recovery.consume(token(first), "GoodPassword123!", "test"); return null; }, "PASSWORD_RESET_INVALID");
        jdbc.sql("UPDATE user_password_reset SET expires_at = now() - interval '1 second' WHERE user_id = :id").param("id", staff).update();
        for (String invalidToken : List.of(token(first), token(next), "unknown-token")) {
            assertCode(() -> { recovery.consume(invalidToken, "GoodPassword123!", "test"); return null; }, "PASSWORD_RESET_INVALID");
        }
    }

    @Test void recoveryCannotTargetOwnersAnotherTenantOrInactiveUsersAndManagerStaysInOwnBranch() {
        assertCode(() -> recovery.issue(data.owner, data.ownerId, "test"), "ACCESS_DENIED");
        UUID anotherOwner = data.user("Dueno del gimnasio", "hash");
        assertCode(() -> recovery.issue(data.owner, anotherOwner, "test"), "USER_NOT_MANAGEABLE");
        var foreign = new OperationalTestData(jdbc);
        assertCode(() -> recovery.issue(data.owner, foreign.user("Coach", "hash"), "test"), "USER_NOT_FOUND");
        UUID staff = data.user("Coach", "hash");
        jdbc.sql("UPDATE user_account SET status = 'SUSPENDED' WHERE id = :id").param("id", staff).update();
        assertCode(() -> recovery.issue(data.owner, staff, "test"), "PASSWORD_RESET_USER_INACTIVE");
        UUID manager = data.user("Gerente", "hash"), coach = data.user("Coach", "hash");
        var principal = new GymOsPrincipal(manager, data.organizationId, data.branchId, "manager", "Manager", Set.of("user.manage"), Set.of("Gerente"), false);
        assertThat(recovery.issue(principal, coach, "test").emailDeliveryStatus()).isEqualTo("SENT");
        assertCode(() -> recovery.issue(principal, anotherOwner, "test"), "USER_NOT_MANAGEABLE");
        UUID otherManager = data.user("Gerente", "hash");
        assertCode(() -> recovery.issue(principal, otherManager, "test"), "USER_NOT_MANAGEABLE");
        UUID noBranchCoach = data.user("Coach", "hash");
        jdbc.sql("DELETE FROM user_branch_access WHERE user_id = :id").param("id", noBranchCoach).update();
        assertCode(() -> recovery.issue(principal, noBranchCoach, "test"), "ACCESS_DENIED");
    }

    @Test void weakPasswordDoesNotConsumeTokenAndConcurrentAcceptanceSucceedsOnlyOnce() throws Exception {
        UUID staff = data.user("Gerente", "hash"); String token = token(recovery.issue(data.owner, staff, "test"));
        assertCode(() -> { recovery.consume(token, "weak", "test"); return null; }, "PASSWORD_POLICY_NOT_MET");
        for (String tooLong : List.of("Aa1!" + "a".repeat(69), "Aa1!" + "ó".repeat(35))) {
            assertCode(() -> { recovery.consume(token, tooLong, "test"); return null; }, "PASSWORD_POLICY_NOT_MET");
        }
        List<String> results = concurrent(() -> {
            try { recovery.consume(token, "GoodPassword123!", "test"); return "CHANGED"; }
            catch (BusinessException error) { return error.code(); }
        });
        assertThat(results).containsExactlyInAnyOrder("CHANGED", "PASSWORD_RESET_INVALID");
        assertThat(new AuthQueryRepository(jdbc).findActiveUserById(staff).orElseThrow().credentialVersion()).isEqualTo(1);
    }

    @Test void changingRoleOrSuspendingRevokesPendingRecoveryAndOldSessions() {
        UUID staff = data.user("Coach", "hash"); String suspensionToken = token(recovery.issue(data.owner, staff, "test"));
        transactions.executeWithoutResult(status -> users.updateUserStatus(data.organizationId, staff, "SUSPENDED", data.ownerId, OffsetDateTime.now()));
        assertCode(() -> { recovery.consume(suspensionToken, "GoodPassword123!", "test"); return null; }, "PASSWORD_RESET_INVALID");
        assertThat(new UserCredentialVersionRepository(jdbc).isCurrent(staff, 0)).isFalse();
        UUID reception = data.user("Recepcion", "hash");
        String roleToken = token(recovery.issue(data.owner, reception, "test"));
        UUID manager = data.user("Gerente", "hash");
        UUID role = jdbc.sql("SELECT role_id FROM user_role WHERE user_id = :id").param("id", manager).query(UUID.class).single();
        transactions.executeWithoutResult(status -> users.replaceRoles(reception, role));
        assertCode(() -> { recovery.consume(roleToken, "GoodPassword123!", "test"); return null; }, "PASSWORD_RESET_INVALID");
        assertThat(new UserCredentialVersionRepository(jdbc).isCurrent(reception, 0)).isFalse();
    }

    private static String token(PasswordRecoveryService.RecoveryIssued issued) { return issued.resetUrl().split("#token=")[1]; }
    private LocalDate today() { return BusinessTime.today(); }
    private <T> T tx(Supplier<T> work) { return transactions.execute(status -> work.get()); }
    private AssignWorkoutCommand command(Workout workout, UUID client, LocalDate start, LocalDate end, String key) {
        return new AssignWorkoutCommand(client, workout.versionId(), start, end, null, null, key);
    }
    private CoachingRepository.WorkoutAssignmentRecord assign(Workout workout, UUID client, LocalDate start, LocalDate end) {
        return tx(() -> workouts.assignWorkout(data.owner, workout.id(), command(workout, client, start, end, UUID.randomUUID().toString()), "test"));
    }
    private void assertCode(Supplier<?> work, String code) {
        if (code.equals("ACCESS_DENIED")) assertThatThrownBy(work::get).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        else assertThatThrownBy(work::get).isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.code()).isEqualTo(code));
    }
    private List<String> concurrent(Supplier<String> operation) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> task = () -> { if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timed out"); return operation.get(); };
            Future<String> first = executor.submit(task), second = executor.submit(task); start.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }
}
