package com.gymos.reporting.api;

import com.gymos.common.security.GymOsPrincipal;
import com.gymos.reporting.application.OwnerDashboardService;
import com.gymos.reporting.application.result.OwnerDashboardResult;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class OwnerDashboardController {
	private final OwnerDashboardService ownerDashboardService;

	public OwnerDashboardController(OwnerDashboardService ownerDashboardService) {
		this.ownerDashboardService = ownerDashboardService;
	}

	@GetMapping("/owner-dashboard")
	OwnerDashboardResult dashboard(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@RequestParam(required = false) LocalDate fromDate,
			@RequestParam(required = false) LocalDate toDate
	) {
		return ownerDashboardService.dashboard(principal, fromDate, toDate);
	}
}
