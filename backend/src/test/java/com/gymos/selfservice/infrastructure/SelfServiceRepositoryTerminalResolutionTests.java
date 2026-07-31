package com.gymos.selfservice.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gymos.selfservice.infrastructure.SelfServiceRepository.TerminalRecord;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

class SelfServiceRepositoryTerminalResolutionTests {
	@Test
	void branchContextWithoutOrganizationFailsClosedWithoutQueryingAnotherTenant() {
		JdbcClient jdbcClient = mock(JdbcClient.class);
		SelfServiceRepository repository = new SelfServiceRepository(jdbcClient);

		Optional<TerminalRecord> result = repository.terminal(
				"KIOSK-MAIN",
				null,
				UUID.randomUUID()
		);

		assertThat(result).isEmpty();
		verifyNoInteractions(jdbcClient);
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void completeReceptionContextUsesOnlyOrganizationAndBranchScopedQuery() {
		JdbcClient jdbcClient = mock(JdbcClient.class);
		JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
		JdbcClient.MappedQuerySpec<TerminalRecord> query = mock(JdbcClient.MappedQuerySpec.class);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();

		when(jdbcClient.sql(anyString())).thenReturn(statement);
		when(statement.param(anyString(), any())).thenReturn(statement);
		when(statement.query(any(RowMapper.class))).thenReturn((JdbcClient.MappedQuerySpec) query);
		when(query.optional()).thenReturn(Optional.empty());

		SelfServiceRepository repository = new SelfServiceRepository(jdbcClient);
		Optional<TerminalRecord> result = repository.terminal(
				"KIOSK-MAIN",
				organizationId,
				branchId
		);

		assertThat(result).isEmpty();
		verify(jdbcClient, times(1)).sql(argThat(sql ->
				sql.contains("sst.organization_id = :organizationId")
						&& sql.contains("sst.branch_id = :branchId")
		));
	}
}
