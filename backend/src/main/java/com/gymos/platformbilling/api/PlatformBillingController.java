package com.gymos.platformbilling.api;

import com.gymos.common.api.Correlation;
import com.gymos.common.security.GymOsPrincipal;
import com.gymos.platformbilling.application.PlatformBillingService;
import com.gymos.platformbilling.application.PlatformBillingService.BillingOverview;
import com.gymos.platformbilling.application.PlatformBillingService.BranchBillingView;
import com.gymos.platformbilling.application.PlatformBillingService.UpdateBillingCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/billing")
public class PlatformBillingController {
    private final PlatformBillingService service;

    public PlatformBillingController(PlatformBillingService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    BillingOverview overview(@AuthenticationPrincipal GymOsPrincipal principal) {
        return service.overview(principal);
    }

    @PutMapping("/branches/{branchId}")
    BranchBillingView updateBranchBilling(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @PathVariable UUID branchId,
            @Valid @RequestBody BranchBillingUpdateRequest request,
            HttpServletRequest servletRequest
    ) {
        return service.update(principal, branchId, request.toCommand(), correlationId(servletRequest));
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(Correlation.ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }

    public record BranchBillingUpdateRequest(
            @NotBlank String subscriptionStatus,
            @Min(1) @Max(31) int billingAnchorDay,
            @NotNull LocalDate nextBillingDate,
            LocalDate lastPaymentDate,
            @DecimalMin("0.0") BigDecimal lastPaymentAmount,
            @NotBlank String contractedPlanCode,
            @DecimalMin("0.0") BigDecimal monthlyPriceOverride,
            @NotNull @DecimalMin("0.0") BigDecimal implementationFee,
            boolean implementationPaid,
            @Size(max = 1000) String notes
    ) {
        private UpdateBillingCommand toCommand() {
            return new UpdateBillingCommand(
                    subscriptionStatus,
                    billingAnchorDay,
                    nextBillingDate,
                    lastPaymentDate,
                    lastPaymentAmount,
                    contractedPlanCode,
                    monthlyPriceOverride,
                    implementationFee,
                    implementationPaid,
                    notes
            );
        }
    }
}
