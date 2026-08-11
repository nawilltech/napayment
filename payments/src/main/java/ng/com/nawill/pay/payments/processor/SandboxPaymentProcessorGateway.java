package ng.com.nawill.pay.payments.processor;

import java.math.BigInteger;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Fake/sandbox processor implementation for MVP v0.1 - always synchronously
 * "succeeds" with a generated reference. No real network call is made.
 * TODO(FR-6/FR-7): replace with real processor integrations + inbound
 * webhook handling once those land in v0.2.
 */
@Component
public class SandboxPaymentProcessorGateway implements PaymentProcessorGateway {

    @Override
    public ProcessorChargeResult charge(BigInteger amount, String currency, String narration) {
        return new ProcessorChargeResult("sandbox_" + UUID.randomUUID(), true);
    }
}
