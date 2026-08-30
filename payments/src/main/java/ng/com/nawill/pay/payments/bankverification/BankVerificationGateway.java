package ng.com.nawill.pay.payments.bankverification;

/**
 * Resolves the account holder's name for an external bank account before
 * it's trusted (Name Enquiry, doc 3 §3 - reduces misdirected-funds fraud).
 * {@link PaystackBankVerificationGateway} is the live implementation; a
 * fake stands in under the {@code test} profile so the integration suite
 * never depends on Paystack's sandbox being reachable.
 */
public interface BankVerificationGateway {

    ResolvedAccount resolveAccountName(String accountNumber, String bankCode);

    record ResolvedAccount(String accountNumber, String accountName) {
    }
}
