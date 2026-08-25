package com.gymos.identity.infrastructure;

import com.gymos.common.security.AccountScope;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IdentityProvisioningRepository {
	private static final long PLATFORM_ADMINISTRATOR_BOOTSTRAP_LOCK_KEY = 0x47594D464F525441L;

	private final JdbcClient jdbcClient;

	public IdentityProvisioningRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public void acquirePlatformAdministratorBootstrapLock() {
		jdbcClient.sql("SELECT pg_advisory_xact_lock(:lockKey)")
				.param("lockKey", PLATFORM_ADMINISTRATOR_BOOTSTRAP_LOCK_KEY)
				.query((resultSet, rowNumber) -> true)
				.single();
	}

	public PlatformAdministratorState platformAdministratorState() {
		String state = jdbcClient.sql("""
				WITH effective_platform_administrators AS (
				    SELECT DISTINCT ua.id, ua.status, ua.locked_until
				FROM user_account ua
				JOIN user_role ur ON ur.user_id = ua.id
				JOIN role r ON r.id = ur.role_id
				JOIN role_permission rp ON rp.role_id = r.id
				JOIN permission p ON p.id = rp.permission_id
				WHERE ua.account_scope = 'PLATFORM'
				  AND r.account_scope = 'PLATFORM'
				  AND ua.deleted_at IS NULL
				  AND r.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				  AND p.code = 'platform.admin'
				)
				SELECT CASE
				    WHEN COUNT(*) FILTER (
				        WHERE status = 'ACTIVE'
				          AND (locked_until IS NULL OR locked_until <= now())
				    ) > 0 THEN 'ACTIVE'
				    WHEN COUNT(*) > 0 THEN 'INACTIVE'
				    ELSE 'NONE'
				END
				FROM effective_platform_administrators
				""").query(String.class).single();
		return PlatformAdministratorState.valueOf(state);
	}

	public Optional<AccountScope> accountScopeForUsername(String username) {
		return jdbcClient.sql("""
				SELECT account_scope
				FROM user_account
				WHERE LOWER(username) = LOWER(:username)
				  AND deleted_at IS NULL
				""")
				.param("username", username)
				.query(String.class)
				.optional()
				.map(AccountScope::valueOf);
	}

	public UUID platformAdministratorRoleId() {
		return jdbcClient.sql("""
				SELECT r.id
				FROM role r
				JOIN role_permission rp ON rp.role_id = r.id
				JOIN permission p ON p.id = rp.permission_id
				WHERE r.account_scope = 'PLATFORM'
				  AND p.code = 'platform.admin'
				  AND r.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				ORDER BY CASE WHEN r.name = 'Administrador GymOS' THEN 0 ELSE 1 END, r.name
				LIMIT 1
				""").query(UUID.class).single();
	}

	public UUID tenantOwnerRoleId(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT r.id
				FROM role r
				WHERE r.account_scope = 'TENANT'
				  AND r.name = 'Dueno del gimnasio'
				  AND (r.organization_id IS NULL OR r.organization_id = :organizationId)
				  AND r.deleted_at IS NULL
				ORDER BY CASE WHEN r.organization_id = :organizationId THEN 0 ELSE 1 END
				LIMIT 1
				""")
				.param("organizationId", organizationId)
				.query(UUID.class)
				.single();
	}

	public UUID createPerson(
			AccountScope scope,
			UUID organizationId,
			String firstName,
			String lastName,
			String email,
			String phone,
			UUID actorUserId,
			OffsetDateTime now
	) {
		UUID personId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO person (
				    id, account_scope, organization_id, first_name, last_name, email, phone, status,
				    created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :accountScope, :organizationId, :firstName, :lastName, :email, :phone, 'ACTIVE',
				    :now, :now, :actorUserId, :actorUserId, 0
				)
				""")
				.param("id", personId)
				.param("accountScope", scope.name())
				.param("organizationId", organizationId)
				.param("firstName", firstName)
				.param("lastName", lastName)
				.param("email", email)
				.param("phone", phone)
				.param("now", now)
				.param("actorUserId", actorUserId)
				.update();
		return personId;
	}

	public UUID createUser(
			AccountScope scope,
			UUID organizationId,
			UUID personId,
			String username,
			String passwordHash,
			String status,
			UUID actorUserId,
			OffsetDateTime now
	) {
		UUID userId = UUID.randomUUID();
		jdbcClient.sql("""
				INSERT INTO user_account (
				    id, account_scope, organization_id, person_id, username, password_hash, status,
				    failed_attempts, created_at, updated_at, created_by, updated_by, version
				)
				VALUES (
				    :id, :accountScope, :organizationId, :personId, :username, :passwordHash, :status,
				    0, :now, :now, :actorUserId, :actorUserId, 0
				)
				""")
				.param("id", userId)
				.param("accountScope", scope.name())
				.param("organizationId", organizationId)
				.param("personId", personId)
				.param("username", username)
				.param("passwordHash", passwordHash)
				.param("status", status)
				.param("now", now)
				.param("actorUserId", actorUserId)
				.update();
		return userId;
	}

	public void assignRole(UUID userId, UUID roleId) {
		jdbcClient.sql("INSERT INTO user_role (user_id, role_id) VALUES (:userId, :roleId)")
				.param("userId", userId)
				.param("roleId", roleId)
				.update();
	}

	public void assignBranch(UUID userId, UUID branchId) {
		jdbcClient.sql("""
				INSERT INTO user_branch_access (user_id, branch_id, access_scope)
				VALUES (:userId, :branchId, 'ADMIN')
				""")
				.param("userId", userId)
				.param("branchId", branchId)
				.update();
	}

	public void createInvitation(
			UUID invitationId,
			UUID userId,
			String tokenHash,
			OffsetDateTime expiresAt,
			UUID actorUserId,
			OffsetDateTime now
	) {
		jdbcClient.sql("""
				INSERT INTO user_invitation (
				    id, user_id, token_hash, status, expires_at, email_delivery_status,
				    created_at, created_by
				)
				VALUES (
				    :id, :userId, :tokenHash, 'PENDING', :expiresAt, 'PENDING',
				    :now, :actorUserId
				)
				""")
				.param("id", invitationId)
				.param("userId", userId)
				.param("tokenHash", tokenHash)
				.param("expiresAt", expiresAt)
				.param("now", now)
				.param("actorUserId", actorUserId)
				.update();
	}

	public Optional<OwnerAccountRecord> lockOwnerAccount(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT ua.id AS user_id, ua.organization_id, ua.status AS account_status,
				       p.email, TRIM(p.first_name || ' ' || p.last_name) AS owner_name
				FROM user_account ua
				JOIN person p ON p.id = ua.person_id
				WHERE ua.organization_id = :organizationId
				  AND ua.account_scope = 'TENANT'
				  AND ua.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				  AND EXISTS (
				      SELECT 1
				      FROM user_role ur
				      JOIN role r ON r.id = ur.role_id
				      WHERE ur.user_id = ua.id
				        AND r.account_scope = 'TENANT'
				        AND r.name = 'Dueno del gimnasio'
				        AND r.deleted_at IS NULL
				  )
				ORDER BY CASE WHEN ua.status = 'INVITED' THEN 0 ELSE 1 END, ua.created_at
				LIMIT 1
				FOR UPDATE OF ua
				""")
				.param("organizationId", organizationId)
				.query((rs, row) -> new OwnerAccountRecord(
						rs.getObject("user_id", UUID.class),
						rs.getObject("organization_id", UUID.class),
						rs.getString("account_status"),
						rs.getString("email"),
						rs.getString("owner_name")))
				.optional();
	}

	public Optional<LatestInvitationRecord> latestInvitation(UUID userId) {
		return jdbcClient.sql("""
				SELECT id, status, expires_at, email_delivery_status, created_at
				FROM user_invitation
				WHERE user_id = :userId
				ORDER BY created_at DESC, id DESC
				LIMIT 1
				""")
				.param("userId", userId)
				.query((rs, row) -> new LatestInvitationRecord(
						rs.getObject("id", UUID.class), rs.getString("status"),
						rs.getObject("expires_at", OffsetDateTime.class),
						rs.getString("email_delivery_status"),
						rs.getObject("created_at", OffsetDateTime.class)))
				.optional();
	}

	public void revokePendingInvitations(UUID userId, OffsetDateTime now) {
		jdbcClient.sql("""
				UPDATE user_invitation
				SET status = CASE WHEN expires_at <= :now THEN 'EXPIRED' ELSE 'REVOKED' END
				WHERE user_id = :userId
				  AND status = 'PENDING'
				""")
				.param("userId", userId)
				.param("now", now)
				.update();
	}

	public List<OwnerInvitationStateRecord> ownerInvitationStates(
			Set<UUID> organizationIds,
			OffsetDateTime now
	) {
		if (organizationIds == null || organizationIds.isEmpty()) return List.of();
		return jdbcClient.sql("""
				SELECT DISTINCT ON (ua.organization_id)
				       ua.organization_id, ua.id AS user_id,
				       TRIM(p.first_name || ' ' || p.last_name) AS owner_name,
				       p.email, ua.status AS account_status,
				       invitation.id AS invitation_id,
				       CASE
				         WHEN invitation.id IS NULL THEN 'MISSING'
				         WHEN invitation.status = 'PENDING' AND invitation.expires_at <= :now THEN 'EXPIRED'
				         ELSE invitation.status
				       END AS invitation_status,
				       invitation.expires_at,
				       COALESCE(invitation.email_delivery_status, 'UNKNOWN') AS email_delivery_status,
				       invitation.last_email_attempt_at,
				       invitation.last_email_sent_at
				FROM user_account ua
				JOIN person p ON p.id = ua.person_id
				LEFT JOIN LATERAL (
				    SELECT i.id, i.status, i.expires_at, i.email_delivery_status,
				           i.last_email_attempt_at, i.last_email_sent_at
				    FROM user_invitation i
				    WHERE i.user_id = ua.id
				    ORDER BY i.created_at DESC, i.id DESC
				    LIMIT 1
				) invitation ON true
				WHERE ua.organization_id IN (:organizationIds)
				  AND ua.account_scope = 'TENANT'
				  AND ua.deleted_at IS NULL
				  AND p.deleted_at IS NULL
				  AND EXISTS (
				      SELECT 1
				      FROM user_role ur
				      JOIN role r ON r.id = ur.role_id
				      WHERE ur.user_id = ua.id
				        AND r.account_scope = 'TENANT'
				        AND r.name = 'Dueno del gimnasio'
				        AND r.deleted_at IS NULL
				  )
				ORDER BY ua.organization_id,
				         CASE WHEN ua.status = 'INVITED' THEN 0 WHEN ua.status = 'ACTIVE' THEN 1 ELSE 2 END,
				         ua.created_at
				""")
				.param("organizationIds", organizationIds)
				.param("now", now)
				.query((rs, row) -> new OwnerInvitationStateRecord(
						rs.getObject("organization_id", UUID.class),
						rs.getObject("user_id", UUID.class),
						rs.getString("owner_name"), rs.getString("email"),
						rs.getString("account_status"),
						rs.getObject("invitation_id", UUID.class),
						rs.getString("invitation_status"),
						rs.getObject("expires_at", OffsetDateTime.class),
						rs.getString("email_delivery_status"),
						rs.getObject("last_email_attempt_at", OffsetDateTime.class),
						rs.getObject("last_email_sent_at", OffsetDateTime.class)))
				.list();
	}

	public void recordInvitationEmailDelivery(
			UUID invitationId,
			String deliveryStatus,
			OffsetDateTime attemptedAt
	) {
		jdbcClient.sql("""
				UPDATE user_invitation
				SET email_delivery_status = :deliveryStatus,
				    email_attempt_count = email_attempt_count + 1,
				    last_email_attempt_at = :attemptedAt,
				    last_email_sent_at = CASE
				        WHEN :deliveryStatus = 'SENT' THEN :attemptedAt
				        ELSE last_email_sent_at
				    END
				WHERE id = :invitationId
				""")
				.param("deliveryStatus", deliveryStatus)
				.param("attemptedAt", attemptedAt)
				.param("invitationId", invitationId)
				.update();
	}

	public Optional<InvitationRecord> lockInvitation(String tokenHash) {
		return jdbcClient.sql("""
				SELECT i.id, i.user_id, i.status, i.expires_at, ua.account_scope, ua.organization_id
				FROM user_invitation i
				JOIN user_account ua ON ua.id = i.user_id
				WHERE i.token_hash = :tokenHash
				FOR UPDATE
				""")
				.param("tokenHash", tokenHash)
				.query((rs, row) -> new InvitationRecord(
						rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
						rs.getString("status"), rs.getObject("expires_at", OffsetDateTime.class),
						AccountScope.valueOf(rs.getString("account_scope")),
						rs.getObject("organization_id", UUID.class)))
				.optional();
	}

	public void expireInvitation(UUID invitationId) {
		jdbcClient.sql("""
				UPDATE user_invitation SET status = 'EXPIRED'
				WHERE id = :id AND status = 'PENDING'
				""").param("id", invitationId).update();
	}

	public int acceptInvitation(UUID invitationId, UUID userId, String passwordHash, OffsetDateTime now) {
		int updated = jdbcClient.sql("""
				UPDATE user_account
				SET password_hash = :passwordHash, status = 'ACTIVE', failed_attempts = 0,
				    locked_until = NULL, updated_at = :now, updated_by = :userId, version = version + 1
				WHERE id = :userId
				  AND account_scope = 'TENANT'
				  AND status = 'INVITED'
				  AND deleted_at IS NULL
				""")
				.param("passwordHash", passwordHash)
				.param("now", now)
				.param("userId", userId)
				.update();
		if (updated == 1) {
			jdbcClient.sql("""
					UPDATE user_invitation
					SET status = 'ACCEPTED', accepted_at = :now
					WHERE id = :id AND status = 'PENDING'
					""")
					.param("now", now)
					.param("id", invitationId)
					.update();
		}
		return updated;
	}

	public record InvitationRecord(
			UUID invitationId,
			UUID userId,
			String status,
			OffsetDateTime expiresAt,
			AccountScope accountScope,
			UUID organizationId
	) {
	}

	public record OwnerAccountRecord(
			UUID userId,
			UUID organizationId,
			String accountStatus,
			String email,
			String ownerName
	) {
	}

	public record LatestInvitationRecord(
			UUID invitationId,
			String status,
			OffsetDateTime expiresAt,
			String emailDeliveryStatus,
			OffsetDateTime createdAt
	) {
	}

	public record OwnerInvitationStateRecord(
			UUID organizationId,
			UUID userId,
			String ownerName,
			String email,
			String accountStatus,
			UUID invitationId,
			String invitationStatus,
			OffsetDateTime invitationExpiresAt,
			String emailDeliveryStatus,
			OffsetDateTime lastEmailAttemptAt,
			OffsetDateTime lastEmailSentAt
	) {
	}

	public enum PlatformAdministratorState {
		NONE,
		ACTIVE,
		INACTIVE
	}
}
