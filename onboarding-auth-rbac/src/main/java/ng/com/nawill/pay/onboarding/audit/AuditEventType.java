package ng.com.nawill.pay.onboarding.audit;

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
    REFRESH_TOKEN_ROTATED,
    REFRESH_TOKEN_REUSE_DETECTED,
    REFRESH_TOKEN_REVOKED,
    BUSINESS_KYC_DETAILS_UPDATED,
    OWNER_IDENTITY_SUBMITTED,
    KYC_DOCUMENT_UPLOADED,
    KYC_SUBMITTED,
    TEAM_INVITATION_CREATED,
    TEAM_INVITATION_REVOKED
    // Invite acceptance is recorded as SIGNUP_VIA_INVITE (AuthService) - the
    // same real-world event, already carrying the invitation id in its detail.
}
