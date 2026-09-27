package ng.com.nawill.pay.payments.transfer;

import java.util.UUID;

/**
 * Verifies a transaction PIN before money moves (FR-Auth-2). Implemented in
 * onboarding-auth-rbac, where the PIN hash and its own lockout state live
 * alongside the user's password - payments never sees the hash or the
 * attempt counter, only a pass/throw through this seam (doc 2 §7 ADR-12,
 * same cross-module ownership shape as {@link RecipientDirectory}).
 */
public interface TransactionPinGateway {

    /**
     * @throws ng.com.nawill.pay.common.exception.ApiException {@code PIN_NOT_SET} if the caller has never set a
     *         PIN, {@code INVALID_PIN} if it is wrong, {@code ACCOUNT_LOCKED} if too many recent wrong attempts
     *         locked the transfer capability
     */
    void verify(UUID userId, String pin);
}
