package ng.com.nawill.pay.onboarding.business;

import ng.com.nawill.pay.common.exception.BadRequestException;
import ng.com.nawill.pay.common.exception.ResourceNotFoundException;
import ng.com.nawill.pay.common.security.CurrentUser;
import ng.com.nawill.pay.common.security.CurrentUserResolver;
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
    private final CurrentUserResolver currentUserResolver;

    public BusinessKycService(BusinessRepository businessRepository, CountryRepository countryRepository,
                               AdminDivisionRepository adminDivisionRepository,
                               CurrentUserResolver currentUserResolver) {
        this.businessRepository = businessRepository;
        this.countryRepository = countryRepository;
        this.adminDivisionRepository = adminDivisionRepository;
        this.currentUserResolver = currentUserResolver;
    }

    public Business updateDetails(BusinessKycDetailsRequest request) {
        Business business = callerBusiness();
        if (!countryRepository.existsById(request.countryId())) {
            throw new BadRequestException("UNKNOWN_COUNTRY", "Unknown country: " + request.countryId());
        }
        if (!adminDivisionRepository.existsById(request.stateId())) {
            throw new BadRequestException("UNKNOWN_STATE", "Unknown state: " + request.stateId());
        }
        business.updateKycDetails(request.registeredName(), request.cacNumber(), request.businessType(),
                request.industry(), request.countryId(), request.stateId(), request.addressLine());
        return businessRepository.save(business);
    }

    @Transactional(readOnly = true)
    public Business getDetails() {
        return callerBusiness();
    }

    private Business callerBusiness() {
        CurrentUser currentUser = currentUserResolver.requireBusinessScope();
        return businessRepository.findById(currentUser.businessId())
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + currentUser.businessId()));
    }
}
