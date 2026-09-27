package ng.com.nawill.pay.common.audit;

/**
 * PCI DSS 10.2.1's auditable-event categories, mapped onto this app's actual
 * events: 10.2.1.4 (invalid access attempts), 10.2.1.5 (changes to
 * identification/authentication credentials, including new account
 * creation), and the onboarding/KYC activity the business asked to have
 * "properly logged" alongside them.
 */
public enum AuditEventType {
    SIGNUP,
    SIGNUP_VIA_INVITE,
    LOGIN,
    ACCOUNT_LOCKED,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED,
    PASSWORD_CHANGED,
    TRANSACTION_PIN_SET,
    TRANSACTION_PIN_VERIFICATION_FAILED,
    TRANSACTION_PIN_LOCKED,
    REFRESH_TOKEN_ROTATED,
    REFRESH_TOKEN_REUSE_DETECTED,
    REFRESH_TOKEN_REVOKED,
    BUSINESS_KYC_DETAILS_UPDATED,
    OWNER_IDENTITY_SUBMITTED,
    KYC_DOCUMENT_UPLOADED,
    KYC_SUBMITTED,
    KYC_APPROVED,
    KYC_REJECTED,
    TEAM_INVITATION_CREATED,
    TEAM_INVITATION_REVOKED,
    // Platform configuration (FR-Admin-7): written by platform staff actions.
    PAYMENT_PROCESSOR_CREATED,
    PAYMENT_PROCESSOR_UPDATED,
    PAYMENT_PROCESSOR_ACTIVATED,
    PAYMENT_PROCESSOR_DEACTIVATED,
    PAYMENT_PROCESSOR_ARCHIVED,
    PAYMENT_PROCESSOR_RESTORED,
    // A processor starting/stopping to offer a catalogue method.
    PAYMENT_METHOD_ENABLED,
    PAYMENT_METHOD_DISABLED,
    // The payment method catalogue itself.
    PAYMENT_METHOD_CREATED,
    PAYMENT_METHOD_UPDATED,
    PAYMENT_METHOD_ACTIVATED,
    PAYMENT_METHOD_DEACTIVATED,
    PAYMENT_METHOD_ARCHIVED,
    PAYMENT_METHOD_RESTORED,
    PAYMENT_PROCESSOR_ENABLED_FOR_ALL,
    PAYMENT_PROCESSOR_DISABLED_FOR_ALL,
    BUSINESS_PAYMENT_PROCESSOR_SET,
    BUSINESS_PAYMENT_PROCESSOR_RESET,
    BUSINESS_DEACTIVATED,
    BUSINESS_ACTIVATED,
    BANK_ACCOUNT_REGISTERED_BY_ADMIN,
    PASSWORD_CONFIRMATION_FAILED
    // Invite acceptance is recorded as SIGNUP_VIA_INVITE (AuthService) - the
    // same real-world event, already carrying the invitation id in its detail.
}
