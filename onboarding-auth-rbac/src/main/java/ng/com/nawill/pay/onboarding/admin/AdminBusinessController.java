package ng.com.nawill.pay.onboarding.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import ng.com.nawill.pay.common.storage.LoadedFile;
import ng.com.nawill.pay.common.entity.EntityStatus;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import ng.com.nawill.pay.onboarding.business.KycStatus;
import ng.com.nawill.pay.onboarding.kyc.KycDocumentService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** FR-3 platform admin: every business on the platform, and the KYC review queue. */
@Tag(name = "Admin - Businesses & KYC", description = "Platform business directory and KYC review.")
@RestController
@RequestMapping("/api/v1/admin")
public class AdminBusinessController {

    private final AdminBusinessService adminBusinessService;
    private final KycDocumentService kycDocumentService;

    public AdminBusinessController(AdminBusinessService adminBusinessService, KycDocumentService kycDocumentService) {
        this.adminBusinessService = adminBusinessService;
        this.kycDocumentService = kycDocumentService;
    }

    @Operation(summary = "List all businesses, with search and KYC status filter")
    @GetMapping("/businesses")
    @PreAuthorize("@auth.can('platform-businesses:read')")
    public PageResponse<AdminBusinessSummaryResponse> list(@RequestParam(required = false) String term,
                                                             @RequestParam(required = false) KycStatus kycStatus,
                                                             @RequestParam(required = false) EntityStatus status,
                                                             @RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                                             @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(adminBusinessService.list(term, kycStatus, status, page, size));
    }

    @Operation(summary = "Count businesses per KYC status")
    @GetMapping("/businesses/stats")
    @PreAuthorize("@auth.can('platform-businesses:read')")
    public BusinessStatsResponse stats() {
        return adminBusinessService.stats();
    }

    @Operation(summary = "Get a business's full KYC review details")
    @GetMapping("/businesses/{id}")
    @PreAuthorize("@auth.can('platform-businesses:read')")
    public AdminBusinessDetailResponse get(@PathVariable UUID id) {
        return adminBusinessService.get(id);
    }

    @Operation(summary = "Approve a business's KYC")
    @PostMapping("/businesses/{id}/kyc/approve")
    @PreAuthorize("@auth.can('platform-kyc:review')")
    public AdminBusinessDetailResponse approveKyc(@PathVariable UUID id) {
        return adminBusinessService.approveKyc(id);
    }

    @Operation(summary = "Reject a business's KYC with a reason")
    @PostMapping("/businesses/{id}/kyc/reject")
    @PreAuthorize("@auth.can('platform-kyc:review')")
    public AdminBusinessDetailResponse rejectKyc(@PathVariable UUID id, @Valid @RequestBody RejectKycRequest request) {
        return adminBusinessService.rejectKyc(id, request.reason());
    }

    @Operation(summary = "Deactivate a business: no money movement or API keys; users keep read-only access")
    @PostMapping("/businesses/{id}/deactivate")
    @PreAuthorize("@auth.can('platform-businesses:manage')")
    public AdminBusinessDetailResponse deactivate(@PathVariable UUID id, @Valid @RequestBody DeactivateBusinessRequest request) {
        return adminBusinessService.deactivate(id, request.reason());
    }

    @Operation(summary = "Reactivate a deactivated business")
    @PostMapping("/businesses/{id}/activate")
    @PreAuthorize("@auth.can('platform-businesses:manage')")
    public AdminBusinessDetailResponse activate(@PathVariable UUID id) {
        return adminBusinessService.activate(id);
    }

    @Operation(summary = "Download any business's KYC document for review")
    @GetMapping("/kyc-documents/{id}/download")
    @PreAuthorize("@auth.can('platform-kyc:review')")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable UUID id) {
        LoadedFile file = kycDocumentService.downloadForReview(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment")
                .body(new InputStreamResource(file.content()));
    }
}
