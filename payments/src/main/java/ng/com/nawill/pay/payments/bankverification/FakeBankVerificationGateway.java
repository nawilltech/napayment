package ng.com.nawill.pay.payments.bankverification;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test-only stand-in for {@link PaystackBankVerificationGateway} - no
 * network call, so the integration suite never depends on Paystack's
 * sandbox being reachable (doc 3 §5.2's "mocked external dependencies"
 * principle, same reasoning as {@code SandboxPaymentProcessorGateway}).
 */
@Component
@Profile("test")
public class FakeBankVerificationGateway implements BankVerificationGateway {

    @Override
    public ResolvedAccount resolveAccountName(String accountNumber, String bankCode) {
        return new ResolvedAccount(accountNumber, "TEST ACCOUNT HOLDER");
    }
}
