package ng.com.nawill.pay.onboarding.business;

import java.util.UUID;
import ng.com.nawill.pay.common.entity.EntityStatus;
import ng.com.nawill.pay.payments.platform.BusinessDirectory;
import org.springframework.stereotype.Component;

/** Implements payments' {@link BusinessDirectory} seam, backed by {@link BusinessRepository}. */
@Component
public class BusinessDirectoryAdapter implements BusinessDirectory {

    private final BusinessRepository businessRepository;

    public BusinessDirectoryAdapter(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    @Override
    public boolean exists(UUID businessId) {
        return businessId != null && businessRepository.existsById(businessId);
    }

    @Override
    public boolean isActive(UUID businessId) {
        return businessId != null && businessRepository.findById(businessId)
                .map(business -> business.getStatus() == EntityStatus.ACTIVE)
                .orElse(false);
    }
}
