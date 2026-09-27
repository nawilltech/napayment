package ng.com.nawill.pay.onboarding.admin;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import ng.com.nawill.pay.common.exception.ConflictException;
import ng.com.nawill.pay.common.exception.ResourceNotFoundException;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.onboarding.audit.AuditEventType;
import ng.com.nawill.pay.onboarding.audit.AuditOutcome;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditService;
import ng.com.nawill.pay.onboarding.business.Business;
import ng.com.nawill.pay.onboarding.business.BusinessRepository;
import ng.com.nawill.pay.onboarding.business.BusinessSpecifications;
import ng.com.nawill.pay.onboarding.business.KycStatus;
import ng.com.nawill.pay.onboarding.kyc.KycDocumentResponse;
import ng.com.nawill.pay.onboarding.kyc.KycDocumentService;
import ng.com.nawill.pay.onboarding.kyc.OwnerIdentityService;
import ng.com.nawill.pay.onboarding.user.User;
import ng.com.nawill.pay.onboarding.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-3: cross-business views and the KYC review decision for the platform
 * admin console. Unlike every other business service, nothing here is scoped
 * to the caller's own business - access is gated on the controller by the
 * platform-* permissions (which SUPERADMIN always passes).
 */
@Service
@Transactional
public class AdminBusinessService {

    private static final Logger log = LoggerFactory.getLogger(AdminBusinessService.class);

    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final KycDocumentService kycDocumentService;
    private final OwnerIdentityService ownerIdentityService;
    private final SecurityAuditService securityAuditService;
    private final CurrentUserResolver currentUserResolver;

    public AdminBusinessService(BusinessRepository businessRepository, UserRepository userRepository,
                                KycDocumentService kycDocumentService, OwnerIdentityService ownerIdentityService,
                                SecurityAuditService securityAuditService, CurrentUserResolver currentUserResolver) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.kycDocumentService = kycDocumentService;
        this.ownerIdentityService = ownerIdentityService;
        this.securityAuditService = securityAuditService;
        this.currentUserResolver = currentUserResolver;
    }

    /**
     * The review queue (PENDING_REVIEW) is ordered oldest submission first, so
     * reviewers work first-in first-out; every other view shows newest businesses first.
     */
    @Transactional(readOnly = true)
    public Page<AdminBusinessSummaryResponse> list(String term, KycStatus kycStatus, int page, int size) {
        Sort sort = kycStatus == KycStatus.PENDING_REVIEW
                ? Sort.by(Sort.Direction.ASC, "kycSubmittedAt")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        Page<Business> businesses = businessRepository.findAll(BusinessSpecifications.matching(term, kycStatus),
                PageRequest.of(page, size, sort));

        Set<UUID> ownerIds = businesses.stream().map(Business::getOwnerId).collect(Collectors.toSet());
        Map<UUID, User> owners = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return businesses.map(business -> AdminBusinessSummaryResponse.from(business, owners.get(business.getOwnerId())));
    }

    @Transactional(readOnly = true)
    public AdminBusinessDetailResponse get(UUID businessId) {
        return detail(find(businessId));
    }

    @Transactional(readOnly = true)
    public BusinessStatsResponse stats() {
        Map<KycStatus, Long> byStatus = new EnumMap<>(KycStatus.class);
        Arrays.stream(KycStatus.values()).forEach(status -> byStatus.put(status, businessRepository.countByKycStatus(status)));
        return new BusinessStatsResponse(businessRepository.count(), byStatus);
    }

    public AdminBusinessDetailResponse approveKyc(UUID businessId) {
        return decide(businessId, KycStatus.VERIFIED, null);
    }

    public AdminBusinessDetailResponse rejectKyc(UUID businessId, String reason) {
        return decide(businessId, KycStatus.REJECTED, reason.trim());
    }

    private AdminBusinessDetailResponse decide(UUID businessId, KycStatus decision, String note) {
        UUID reviewerId = currentUserResolver.requireCurrentUser().userId();
        Business business = find(businessId);
        if (!business.isAwaitingKycReview()) {
            throw new ConflictException("KYC is not awaiting review (status: " + business.getKycStatus() + ")");
        }
        business.recordKycDecision(decision, reviewerId, note);
        business = businessRepository.save(business);

        boolean approved = decision == KycStatus.VERIFIED;
        log.info("kyc {}: businessId={} reviewerId={}", approved ? "approved" : "rejected", businessId, reviewerId);
        securityAuditService.record(approved ? AuditEventType.KYC_APPROVED : AuditEventType.KYC_REJECTED,
                AuditOutcome.SUCCESS, reviewerId, businessId, approved ? "Approved in platform review" : "Rejected: " + note);
        return detail(business);
    }

    private Business find(UUID businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));
    }

    private AdminBusinessDetailResponse detail(Business business) {
        User owner = userRepository.findById(business.getOwnerId()).orElse(null);
        return AdminBusinessDetailResponse.from(business, owner,
                ownerIdentityService.findMaskedForBusiness(business.getId()).orElse(null),
                kycDocumentService.listForBusiness(business.getId()).stream().map(KycDocumentResponse::from).toList());
    }
}
