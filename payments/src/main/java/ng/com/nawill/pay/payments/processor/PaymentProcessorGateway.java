package ng.com.nawill.pay.payments.processor;

import java.math.BigInteger;

/**
 * Behind-an-interface contract for payment processor integrations (Paystack,
 * Interswitch, Flutterwave, ...), per doc 2 §1.1's dependency-inverted
 * integration principle. {@link SandboxPaymentProcessorGateway} is the only
 * implementation for MVP v0.1; real processor integrations are v0.2+.
 */
public interface PaymentProcessorGateway {

    ProcessorChargeResult charge(BigInteger amount, String currency, String narration);

    record ProcessorChargeResult(String processorReference, boolean successful) {
    }
}
