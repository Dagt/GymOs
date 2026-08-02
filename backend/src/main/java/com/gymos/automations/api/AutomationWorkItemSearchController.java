package com.gymos.automations.api;

import com.gymos.automations.infrastructure.AutomationWorkItemSearchRepository;
import com.gymos.automations.infrastructure.AutomationWorkItemSearchRepository.Page;
import com.gymos.common.api.BusinessException;
import com.gymos.common.security.GymOsPrincipal;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/automations/work-items")
public class AutomationWorkItemSearchController {
    private static final Set<String> STATUSES = Set.of(
            "PENDING", "RUNNING", "RETRY_SCHEDULED", "SUCCEEDED", "DEAD_LETTER", "CANCELLED"
    );

    private final AutomationWorkItemSearchRepository searchRepository;

    public AutomationWorkItemSearchController(AutomationWorkItemSearchRepository searchRepository) {
        this.searchRepository = searchRepository;
    }

    @GetMapping("/page")
    Page page(
            @AuthenticationPrincipal GymOsPrincipal principal,
            @RequestParam(required = false) UUID ruleId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String triggerReference,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) OffsetDateTime cursorCreatedAt,
            @RequestParam(required = false) UUID cursorId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        requireRead(principal);
        String normalizedStatus = normalizeStatus(status);
        if ((cursorCreatedAt == null) != (cursorId == null)) {
            throw new BusinessException(
                    "AUTOMATION_CURSOR_INVALID",
                    "El cursor requiere fecha e identificador."
            );
        }
        return searchRepository.search(
                principal.organizationId(), principal.branchId(), ruleId, normalizedStatus,
                blankToNull(triggerReference), from, to, cursorCreatedAt, cursorId, limit
        );
    }

    private void requireRead(GymOsPrincipal principal) {
        if (principal == null) {
            throw new BusinessException("AUTHENTICATION_REQUIRED", "Debes iniciar sesion para continuar.");
        }
        if (principal.branchId() == null) {
            throw new BusinessException("BRANCH_REQUIRED", "Selecciona una sucursal para operar.");
        }
        if (!principal.permissions().contains("automation.read")
                && !principal.permissions().contains("automation.manage")
                && !principal.permissions().contains("platform.admin")) {
            throw new BusinessException("FORBIDDEN", "No tienes permiso para consultar automatizaciones.");
        }
    }

    private String normalizeStatus(String status) {
        String normalized = blankToNull(status);
        if (normalized == null) return null;
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new BusinessException("AUTOMATION_WORK_ITEM_STATUS_INVALID", "El estado solicitado no es valido.");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
