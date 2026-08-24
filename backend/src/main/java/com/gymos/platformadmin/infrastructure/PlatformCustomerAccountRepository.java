package com.gymos.platformadmin.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PlatformCustomerAccountRepository {
	private final JdbcClient jdbcClient;

	public PlatformCustomerAccountRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public List<CustomerAccountRecord> findAll() {
		return jdbcClient.sql("""
				SELECT organization_id, lifecycle_status, account_owner, billing_email,
				       billing_phone, contract_started_on, renewal_on, payment_terms_days,
				       next_follow_up_on, notes, version, updated_at
				FROM platform_customer_account
				ORDER BY organization_id
				""")
				.query(this::mapAccount)
				.list();
	}

	public Optional<CustomerAccountRecord> find(UUID organizationId) {
		return jdbcClient.sql("""
				SELECT organization_id, lifecycle_status, account_owner, billing_email,
				       billing_phone, contract_started_on, renewal_on, payment_terms_days,
				       next_follow_up_on, notes, version, updated_at
				FROM platform_customer_account
				WHERE organization_id = :organizationId
				""")
				.param("organizationId", organizationId)
				.query(this::mapAccount)
				.optional();
	}

	public int upsert(CustomerAccountWrite write) {
		return jdbcClient.sql("""
				INSERT INTO platform_customer_account (
				    organization_id, lifecycle_status, account_owner, billing_email,
				    billing_phone, contract_started_on, renewal_on, payment_terms_days,
				    next_follow_up_on, notes, version, created_at, updated_at, updated_by
				) VALUES (
				    :organizationId, :lifecycleStatus, :accountOwner, :billingEmail,
				    :billingPhone, :contractStartedOn, :renewalOn, :paymentTermsDays,
				    :nextFollowUpOn, :notes, 1, :now, :now, :updatedBy
				)
				ON CONFLICT (organization_id) DO UPDATE SET
				    lifecycle_status = EXCLUDED.lifecycle_status,
				    account_owner = EXCLUDED.account_owner,
				    billing_email = EXCLUDED.billing_email,
				    billing_phone = EXCLUDED.billing_phone,
				    contract_started_on = EXCLUDED.contract_started_on,
				    renewal_on = EXCLUDED.renewal_on,
				    payment_terms_days = EXCLUDED.payment_terms_days,
				    next_follow_up_on = EXCLUDED.next_follow_up_on,
				    notes = EXCLUDED.notes,
				    version = platform_customer_account.version + 1,
				    updated_at = EXCLUDED.updated_at,
				    updated_by = EXCLUDED.updated_by
				WHERE platform_customer_account.version = :expectedVersion
				""")
				.param("organizationId", write.organizationId())
				.param("lifecycleStatus", write.lifecycleStatus())
				.param("accountOwner", write.accountOwner())
				.param("billingEmail", write.billingEmail())
				.param("billingPhone", write.billingPhone())
				.param("contractStartedOn", write.contractStartedOn())
				.param("renewalOn", write.renewalOn())
				.param("paymentTermsDays", write.paymentTermsDays())
				.param("nextFollowUpOn", write.nextFollowUpOn())
				.param("notes", write.notes())
				.param("expectedVersion", write.expectedVersion())
				.param("now", write.now())
				.param("updatedBy", write.updatedBy())
				.update();
	}

	private CustomerAccountRecord mapAccount(ResultSet resultSet, int rowNumber) throws SQLException {
		return new CustomerAccountRecord(
				resultSet.getObject("organization_id", UUID.class),
				resultSet.getString("lifecycle_status"),
				resultSet.getString("account_owner"),
				resultSet.getString("billing_email"),
				resultSet.getString("billing_phone"),
				resultSet.getObject("contract_started_on", LocalDate.class),
				resultSet.getObject("renewal_on", LocalDate.class),
				resultSet.getInt("payment_terms_days"),
				resultSet.getObject("next_follow_up_on", LocalDate.class),
				resultSet.getString("notes"),
				resultSet.getLong("version"),
				resultSet.getObject("updated_at", OffsetDateTime.class)
		);
	}

	public record CustomerAccountRecord(
			UUID organizationId,
			String lifecycleStatus,
			String accountOwner,
			String billingEmail,
			String billingPhone,
			LocalDate contractStartedOn,
			LocalDate renewalOn,
			int paymentTermsDays,
			LocalDate nextFollowUpOn,
			String notes,
			long version,
			OffsetDateTime updatedAt
	) {
	}

	public record CustomerAccountWrite(
			UUID organizationId,
			String lifecycleStatus,
			String accountOwner,
			String billingEmail,
			String billingPhone,
			LocalDate contractStartedOn,
			LocalDate renewalOn,
			int paymentTermsDays,
			LocalDate nextFollowUpOn,
			String notes,
			long expectedVersion,
			UUID updatedBy,
			OffsetDateTime now
	) {
	}
}
