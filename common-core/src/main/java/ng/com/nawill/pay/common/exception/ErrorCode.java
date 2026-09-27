package ng.com.nawill.pay.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The single catalogue of every error the API returns: its stable
 * {@code errorCode}, HTTP status and user-facing message. Nothing else in
 * the codebase writes an error code or message inline - throw
 * {@code new ApiException(ErrorCode.X, args...)} instead. Messages are shown
 * to end users verbatim by the frontends, so they are written as UI copy and
 * never carry internal ids, enum names or third-party provider text;
 * {@code %s}/{@code %d} placeholders are filled from the exception's args.
 * The frontends mirror these names in {@code @napayment/api-client}'s
 * {@code ERROR_CODES}.
 */
public enum ErrorCode {

    // ---- Request / platform ------------------------------------------------
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Some details are missing or invalid."),
    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "This endpoint does not exist."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This action is not supported on this endpoint."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This content type is not supported."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side. Please try again."),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait a moment and try again."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "The %s header is required for this operation."),
    IDEMPOTENCY_IN_PROGRESS(HttpStatus.CONFLICT, "A request with this idempotency key is still processing."),

    // ---- Authentication & access -------------------------------------------
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You don't have permission to do this."),
    BUSINESS_ACCOUNT_REQUIRED(HttpStatus.FORBIDDEN, "This is only available to business accounts."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password (attempt %d/%d)."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Too many failed attempts. Try again in %d minute(s)."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Your session is no longer valid. Please sign in again."),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Your session is no longer valid. Please sign in again."),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again."),
    INCORRECT_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "Your current password is incorrect."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "The passwords do not match."),
    PASSWORD_REUSED(HttpStatus.BAD_REQUEST, "You can't reuse one of your last %d passwords. Choose a different password."),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "The reset code is invalid or has expired."),
    EMAIL_TAKEN(HttpStatus.BAD_REQUEST, "An account with this email already exists."),
    PHONE_TAKEN(HttpStatus.BAD_REQUEST, "An account with this phone number already exists."),
    INVALID_INVITE(HttpStatus.BAD_REQUEST, "This invite is no longer valid."),
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Invitation not found."),
    PIN_MISMATCH(HttpStatus.BAD_REQUEST, "The PINs do not match."),
    PIN_NOT_SET(HttpStatus.BAD_REQUEST, "Set a transaction PIN before sending a transfer."),
    INCORRECT_CURRENT_PIN(HttpStatus.BAD_REQUEST, "Your current PIN is incorrect."),
    INVALID_PIN(HttpStatus.BAD_REQUEST, "Incorrect transaction PIN (attempt %d/%d)."),
    PASSWORD_CONFIRMATION_FAILED(HttpStatus.BAD_REQUEST, "Incorrect password (attempt %d/%d)."),
    UNKNOWN_PERMISSION(HttpStatus.BAD_REQUEST, "One or more of the selected permissions do not exist."),

    // ---- API keys (server-to-server) ---------------------------------------
    MISSING_SIGNATURE_HEADERS(HttpStatus.UNAUTHORIZED, "The X-Public-Key, X-Timestamp and X-Signature headers are all required."),
    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "Unknown or inactive API key."),
    STALE_TIMESTAMP(HttpStatus.UNAUTHORIZED, "The request timestamp is outside the allowed window of %d seconds."),
    INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "The request signature could not be verified."),
    IP_NOT_WHITELISTED(HttpStatus.FORBIDDEN, "This IP address is not on the API key's whitelist."),
    API_KEY_ALREADY_EXISTS(HttpStatus.CONFLICT, "An API key already exists for this business. Regenerate it instead."),
    API_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "No active API key yet. Generate one first."),
    INVALID_URL(HttpStatus.BAD_REQUEST, "Enter a valid URL."),

    // ---- Onboarding & KYC --------------------------------------------------
    BUSINESS_NOT_FOUND(HttpStatus.NOT_FOUND, "Business not found."),
    BUSINESS_INACTIVE(HttpStatus.FORBIDDEN, "This business has been deactivated, so it can't receive or move money. Contact Napayment support."),
    UNKNOWN_COUNTRY(HttpStatus.BAD_REQUEST, "Choose a valid country."),
    UNKNOWN_STATE(HttpStatus.BAD_REQUEST, "Choose a valid state."),
    COUNTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "Country not found."),
    KYC_DETAILS_INCOMPLETE(HttpStatus.BAD_REQUEST, "Complete your business details first."),
    KYC_DOCUMENTS_MISSING(HttpStatus.BAD_REQUEST, "Upload the missing documents: %s."),
    KYC_NOT_AWAITING_REVIEW(HttpStatus.CONFLICT, "This business's KYC is not awaiting review."),
    FILE_REQUIRED(HttpStatus.BAD_REQUEST, "Choose a file to upload."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "The file must be %d MB or smaller."),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Only PDF, PNG or JPEG files are accepted."),
    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Document not found."),
    OWNER_ID_REQUIRED(HttpStatus.BAD_REQUEST, "Provide either a BVN or a NIN."),
    INVALID_BVN(HttpStatus.BAD_REQUEST, "BVN must be 11 digits."),
    INVALID_NIN(HttpStatus.BAD_REQUEST, "NIN must be 11 digits."),

    // ---- Banks & accounts --------------------------------------------------
    UNKNOWN_BANK(HttpStatus.BAD_REQUEST, "Choose a valid bank."),
    BANK_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "We couldn't verify this account. Check the bank and account number."),
    BANK_VERIFICATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Account verification is unavailable right now. Try again later."),
    BANK_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Bank account not found."),
    BANK_ACCOUNT_ALREADY_REGISTERED(HttpStatus.CONFLICT, "This bank account is already registered for the business."),
    COLLECTION_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "No collection account has been set up yet."),
    COLLECTION_ACCOUNT_ALREADY_ACTIVE(HttpStatus.CONFLICT, "A collection account is already active. Deactivate it before creating another."),
    VIRTUAL_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found."),
    DYNAMIC_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Temporary account not found."),
    DYNAMIC_ACCOUNT_NOT_DEPOSITABLE(HttpStatus.BAD_REQUEST, "This account is %s and can't accept deposits."),

    // ---- Money movement ----------------------------------------------------
    PAYMENT_METHOD_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "%s payments aren't available for this account right now."),
    PAYMENT_PROCESSOR_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment processor not found."),
    PAYMENT_PROCESSOR_UNAVAILABLE(HttpStatus.BAD_REQUEST, "This payment processor isn't available for this account."),
    PAYMENT_PROCESSOR_NAME_TAKEN(HttpStatus.CONFLICT, "A payment processor with this name already exists."),
    PAYMENT_PROCESSOR_CODE_TAKEN(HttpStatus.CONFLICT, "A payment processor with this code already exists."),
    INVALID_LOGO(HttpStatus.BAD_REQUEST, "The logo must be a PNG, JPEG or WebP image of %d KB or less."),
    PAYMENT_METHOD_NOT_OFFERED(HttpStatus.NOT_FOUND, "This processor doesn't offer %s."),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Transaction not found."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "The start date can't be after the end date."),
    INVALID_AMOUNT_RANGE(HttpStatus.BAD_REQUEST, "The minimum amount can't be more than the maximum amount."),
    INSUFFICIENT_BALANCE(HttpStatus.BAD_REQUEST, "Insufficient balance."),
    SPLIT_PERCENTAGE_EXCEEDED(HttpStatus.BAD_REQUEST, "Split percentages would total %s%%, which is more than 100%%."),
    NO_SETTLEMENT_ACCOUNTS(HttpStatus.BAD_REQUEST, "Add a settlement account before settling."),
    NOTHING_TO_SETTLE(HttpStatus.BAD_REQUEST, "There is nothing to settle."),
    RECIPIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "No Nawill account matches %s."),
    SELF_TRANSFER(HttpStatus.BAD_REQUEST, "You can't send money to yourself."),
    CURRENCY_MISMATCH(HttpStatus.BAD_REQUEST, "The sender and recipient accounts use different currencies."),
    PAYMENT_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment link not found."),
    PAYMENT_LINK_NOT_PAYABLE(HttpStatus.BAD_REQUEST, "This payment link is %s and can't be paid."),
    PAYMENT_LINK_EXPIRY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "A permanent payment link can't have an expiry date."),
    AMOUNT_REQUIRED(HttpStatus.BAD_REQUEST, "Enter the amount to pay.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    /** The user-facing message with any placeholders filled from {@code args}. */
    public String message(Object... args) {
        return args.length == 0 ? message : message.formatted(args);
    }
}
