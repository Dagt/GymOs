package com.gymos.selfservice.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.selfservice.application.KioskSettingsService;
import com.gymos.selfservice.application.KioskSettingsService.BranchKioskSettings;
import com.gymos.selfservice.application.KioskSettingsService.UpdateKioskSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/kiosk-settings")
public class KioskSettingsController {
	private final KioskSettingsService service;

	public KioskSettingsController(KioskSettingsService service) {
		this.service = service;
	}

	@GetMapping("/branches")
	List<BranchKioskSettings> branches(@AuthenticationPrincipal GymOsPrincipal principal) {
		return service.configurations(principal);
	}

	@PutMapping("/branches/{branchId}")
	BranchKioskSettings configure(
			@AuthenticationPrincipal GymOsPrincipal principal,
			@PathVariable UUID branchId,
			@Valid @RequestBody UpdateKioskSettings request,
			HttpServletRequest servletRequest
	) {
		Object correlationId = servletRequest.getAttribute(Correlation.ATTRIBUTE);
		return service.configure(principal, branchId, request, correlationId == null ? "unknown" : correlationId.toString());
	}
}
