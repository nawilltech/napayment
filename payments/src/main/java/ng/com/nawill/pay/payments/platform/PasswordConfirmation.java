package ng.com.nawill.pay.payments.platform;

import java.util.UUID;

/**
 * Re-checks the signed-in staff member's password before a platform-wide
 * action (FR-Admin-5). Implemented in onboarding-auth-rbac; wrong attempts
 * count toward the normal login lockout.
 */
public interface PasswordConfirmation {

    /**
     * @throws ng.com.nawill.pay.common.exception.ApiException {@code PASSWORD_CONFIRMATION_FAILED} if the password
     *         is wrong, {@code ACCOUNT_LOCKED} once too many attempts have failed
     */
    void confirm(UUID userId, String password);
}
