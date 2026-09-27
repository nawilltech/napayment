package ng.com.nawill.pay.payments.platform;

import java.util.UUID;

/**
 * Business existence/status as payments needs it. Business lives in
 * onboarding-auth-rbac, which depends on payments (not the reverse), so
 * onboarding implements this - same dependency-inverted shape as
 * {@link ng.com.nawill.pay.payments.transfer.TransactionPinGateway}.
 */
public interface BusinessDirectory {

    boolean exists(UUID businessId);

    /** False for a deactivated (FR-Admin-6) or unknown business. */
    boolean isActive(UUID businessId);
}
