package ng.com.nawill.pay.onboarding.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.UUID;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import ng.com.nawill.pay.common.audit.AuditEventType;
import ng.com.nawill.pay.common.audit.AuditOutcome;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Doc 4 §C.5 platform audit-log viewer - see {@link AdminAuditLogService}. */
@Tag(name = "Admin - Audit Log", description = "Platform-wide security audit log.")
@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    public AdminAuditLogController(AdminAuditLogService adminAuditLogService) {
        this.adminAuditLogService = adminAuditLogService;
    }

    @Operation(summary = "Search the security audit log across all businesses")
    @GetMapping
    @PreAuthorize("@auth.can('platform-audit:read')")
    public PageResponse<AuditLogResponse> list(@RequestParam(required = false) AuditEventType eventType,
                                                @RequestParam(required = false) AuditOutcome outcome,
                                                @RequestParam(required = false) UUID userId,
                                                @RequestParam(required = false) UUID businessId,
                                                @RequestParam(required = false) String email,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
                                                @RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(
                adminAuditLogService.list(eventType, outcome, userId, businessId, email, fromDate, toDate, page, size));
    }
}
