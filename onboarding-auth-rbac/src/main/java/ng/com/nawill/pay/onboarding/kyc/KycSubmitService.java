package ng.com.nawill.pay.onboarding.kyc;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.onboarding.audit.AuditEventType;
import ng.com.nawill.pay.onboarding.audit.AuditOutcome;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditService;
import ng.com.nawill.pay.onboarding.business.Business;
import ng.com.nawill.pay.onboarding.business.BusinessRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR-8, feeds FR-3's admin review queue. TODO(doc 2 §1.2): a real deployment
 * would publish a {@code kyc.submitted} event onto the Kafka backbone for the
 * admin portal to pick up - this codebase has no event backbone yet (only
 * Postgres+Redis), so this just logs the submission, same posture as
 * {@code TransactionService#logTransition}.
 */
@Service
@Transactional
public class KycSubmitService {

    private static final Logger log = LoggerFactory.getLogger(KycSubmitService.class);

    private final BusinessRepository businessRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final SecurityAuditService securityAuditService;
    private final CurrentUserResolver currentUserResolver;

    public KycSubmitService(BusinessRepository businessRepository, KycDocumentRepository kycDocumentRepository,
                             SecurityAuditService securityAuditService, CurrentUserResolver currentUserResolver) {
        this.businessRepository = businessRepository;
        this.kycDocumentRepository = kycDocumentRepository;
        this.securityAuditService = securityAuditService;
        this.currentUserResolver = currentUserResolver;
    }

    public KycSubmitResponse submit() {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        Business business = businessRepository.findById(currentUser.businessId())
                .orElseThrow(() -> new ApiException(ErrorCode.BUSINESS_NOT_FOUND));

        if (business.getKycDetailsUpdatedAt() == null) {
            throw new ApiException(ErrorCode.KYC_DETAILS_INCOMPLETE);
        }

        Set<KycDocumentType> uploaded = kycDocumentRepository.findByBusinessId(currentUser.businessId()).stream()
                .map(KycDocument::getDocumentType)
                .collect(Collectors.toSet());
        List<KycDocumentType> missing = Arrays.stream(KycDocumentType.values())
                .filter(type -> !uploaded.contains(type))
                .toList();
        if (!missing.isEmpty()) {
            String names = missing.stream().map(KycDocumentType::label).collect(Collectors.joining(", "));
            throw new ApiException(ErrorCode.KYC_DOCUMENTS_MISSING, names);
        }

        business.submitKycForReview();
        business = businessRepository.save(business);
        log.info("kyc submitted for review: businessId={}", currentUser.businessId());
        securityAuditService.record(AuditEventType.KYC_SUBMITTED, AuditOutcome.SUCCESS, currentUser.userId(),
                currentUser.businessId(), "All 4 documents present");

        return new KycSubmitResponse(business.getKycStatus(), business.getKycSubmittedAt());
    }
}
