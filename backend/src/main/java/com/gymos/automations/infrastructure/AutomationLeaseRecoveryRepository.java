package com.gymos.automations.infrastructure;

import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AutomationLeaseRecoveryRepository {
	private static final String ABANDONED_WORKER_ERROR =
			"La ejecucion fue recuperada porque el worker anterior no libero el lease.";

	private final JdbcClient jdbcClient;

	public AutomationLeaseRecoveryRepository(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	public int recoverExpiredLeases(OffsetDateTime now) {
		return jdbcClient.sql("""
				UPDATE automation_work_item
				SET status = CASE
				        WHEN attempt_count >= max_attempts THEN 'DEAD_LETTER'
				        ELSE 'RETRY_SCHEDULED'
				    END,
				    next_attempt_at = CASE
				        WHEN attempt_count >= max_attempts THEN next_attempt_at
				        ELSE :now
				    END,
				    finished_at = CASE
				        WHEN attempt_count >= max_attempts THEN :now
				        ELSE NULL
				    END,
				    locked_until = NULL,
				    last_error = :error,
				    updated_at = :now,
				    version = version + 1
				WHERE status = 'RUNNING'
				  AND locked_until IS NOT NULL
				  AND locked_until <= :now
				""")
				.param("now", now)
				.param("error", ABANDONED_WORKER_ERROR)
				.update();
	}
}
