package com.gymos.coaching.application;

import com.gymos.audit.api.AuditService;
import com.gymos.coaching.infrastructure.CoachingRepository;
import com.gymos.common.time.BusinessTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "gymos.coaching.expiration", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AssignmentExpirationWorker {
	private final CoachingRepository repository;
	private final AuditService audit;
	private final int batchSize;

	public AssignmentExpirationWorker(CoachingRepository repository, AuditService audit,
			@Value("${gymos.coaching.expiration.batch-size:250}") int batchSize) {
		this.repository = repository;
		this.audit = audit;
		this.batchSize = Math.max(1, Math.min(batchSize, 1000));
	}

	@Scheduled(fixedDelayString = "${gymos.coaching.expiration.delay-ms:60000}")
	@Transactional
	public void completeDueAssignments() {
		for (var assignment : repository.completeDueAssignments(BusinessTime.today(), batchSize)) {
			audit.recordSystem(assignment.organizationId(), assignment.branchId(), null,
					"WORKOUT_ASSIGNMENT_PERIOD_ENDED", "WORKOUT_ASSIGNMENT", assignment.id(),
					"SCHEDULER", "PERIOD_ENDED", "assignment-expiration");
		}
	}
}
