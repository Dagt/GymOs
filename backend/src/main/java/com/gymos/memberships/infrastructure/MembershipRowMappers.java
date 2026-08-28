package com.gymos.memberships.infrastructure;

import com.gymos.memberships.infrastructure.MembershipRepository.MembershipSummary;
import com.gymos.memberships.infrastructure.MembershipRepository.PlanForRenewal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

final class MembershipRowMappers {
	private MembershipRowMappers() {
	}

	static MembershipSummary membershipSummary(ResultSet resultSet, int rowNumber) throws SQLException {
		return new MembershipSummary(
				resultSet.getObject("id", UUID.class),
				resultSet.getObject("client_id", UUID.class),
				resultSet.getObject("plan_id", UUID.class),
				resultSet.getString("plan_name"),
				resultSet.getObject("sale_id", UUID.class),
				resultSet.getString("sale_number"),
				resultSet.getString("status"),
				resultSet.getObject("activated_at", OffsetDateTime.class),
				resultSet.getObject("starts_on", LocalDate.class),
				resultSet.getObject("ends_on", LocalDate.class),
				resultSet.getObject("grace_ends_on", LocalDate.class)
		);
	}

	static PlanForRenewal planForRenewal(ResultSet resultSet, int rowNumber) throws SQLException {
		return new PlanForRenewal(
				resultSet.getObject("plan_id", UUID.class),
				resultSet.getObject("product_id", UUID.class),
				resultSet.getString("plan_name"),
				resultSet.getInt("duration_days"),
				resultSet.getInt("grace_days"),
				resultSet.getBoolean("multi_branch_access"),
				resultSet.getString("registration_fee_policy"),
				resultSet.getLong("price_cents"),
				resultSet.getString("currency")
		);
	}
}
