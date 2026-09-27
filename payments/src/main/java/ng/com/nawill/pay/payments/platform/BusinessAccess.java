package ng.com.nawill.pay.payments.platform;

import java.util.UUID;
import ng.com.nawill.pay.common.exception.ApiException;
import ng.com.nawill.pay.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

/**
 * The one check every money-moving path makes (FR-Admin-6): a deactivated
 * business can neither receive nor move money. Accounts without a business
 * (individuals) pass.
 */
@Service
public class BusinessAccess {

    private final BusinessDirectory businessDirectory;

    public BusinessAccess(BusinessDirectory businessDirectory) {
        this.businessDirectory = businessDirectory;
    }

    public void requireActive(UUID businessId) {
        if (!isActive(businessId)) {
            throw new ApiException(ErrorCode.BUSINESS_INACTIVE);
        }
    }

    /** Individuals (no business) count as active. */
    public boolean isActive(UUID businessId) {
        return businessId == null || businessDirectory.isActive(businessId);
    }

    public void requireExists(UUID businessId) {
        if (!businessDirectory.exists(businessId)) {
            throw new ApiException(ErrorCode.BUSINESS_NOT_FOUND);
        }
    }
}
