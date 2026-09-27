package ng.com.nawill.pay.onboarding.business;

import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
import ng.com.nawill.pay.onboarding.audit.AuditEventType;
import ng.com.nawill.pay.onboarding.audit.AuditOutcome;
import ng.com.nawill.pay.onboarding.audit.SecurityAuditService;
import ng.com.nawill.pay.referencedata.repository.AdminDivisionRepository;
import ng.com.nawill.pay.referencedata.repository.CountryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR-8 business KYB: the "business details" step of onboarding. */
@Service
@Transactional
public class BusinessKycService {

    private final BusinessRepository businessRepository;
    private final CountryRepository countryRepository;
    private final AdminDivisionRepository adminDivisionRepository;
    private final CacLookupGateway cacLookupGateway;
    private final SecurityAuditService securityAuditService;
    private final CurrentUserResolver currentUserResolver;

    public BusinessKycService(BusinessRepository businessRepository, CountryRepository countryRepository,
                               AdminDivisionRepository adminDivisionRepository, CacLookupGateway cacLookupGateway,
                               SecurityAuditService securityAuditService, CurrentUserResolver currentUserResolver) {
        this.businessRepository = businessRepository;
        this.countryRepository = countryRepository;
        this.adminDivisionRepository = adminDivisionRepository;
        this.cacLookupGateway = cacLookupGateway;
        this.securityAuditService = securityAuditService;
        this.currentUserResolver = currentUserResolver;
    }

    public Business updateDetails(BusinessKycDetailsRequest request) {
        Business business = callerBusiness();
        if (!countryRepository.existsById(request.countryId())) {
            throw new ApiException(ErrorCode.UNKNOWN_COUNTRY);
        }
        if (!adminDivisionRepository.existsById(request.stateId())) {
            throw new ApiException(ErrorCode.UNKNOWN_STATE);
        }
        CacLookupResult cacLookupResult = cacLookupGateway.lookup(request.cacNumber(), request.registeredName());
        business.updateKycDetails(request.registeredName(), request.cacNumber(), request.businessType(),
                request.industry(), request.countryId(), request.stateId(), request.addressLine(), cacLookupResult);
        business = businessRepository.save(business);

        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        securityAuditService.record(AuditEventType.BUSINESS_KYC_DETAILS_UPDATED, AuditOutcome.SUCCESS,
                currentUser.userId(), business.getId(),
                "CAC " + (cacLookupResult.verified() ? "verified" : "unverified") + " via " + cacLookupResult.source());
        return business;
    }

    @Transactional(readOnly = true)
    public Business getDetails() {
        return callerBusiness();
    }

    private Business callerBusiness() {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        return businessRepository.findById(currentUser.businessId())
                .orElseThrow(() -> new ApiException(ErrorCode.BUSINESS_NOT_FOUND));
    }
}
